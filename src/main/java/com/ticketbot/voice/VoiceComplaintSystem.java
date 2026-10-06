package com.ticketbot.voice;

import com.ticketbot.config.BotConfig;
import com.ticketbot.ticket.StaffPermissions;
import com.ticketbot.ticket.Ticket;
import com.ticketbot.ticket.TicketIds;
import com.ticketbot.ticket.TicketPanel;
import com.ticketbot.ticket.TicketStore;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * نظام الشكوى الصوتية (أزرار voice_start / voice_stop داخل تذكرة "شكوى صوتية على إداري").
 *
 * أهم قاعدة: جلسة واحدة فقط لكل سيرفر (Discord يسمح للبوت باتصال صوتي واحد لكل سيرفر).
 * sessions.putIfAbsent(...) عملية ذرية (Atomic): لو ضغط شخصان "بدء التسجيل" بنفس اللحظة،
 * واحد فقط سينجح => لا يوجد اتصالان متداخلان.
 *
 * هذا الكلاس لا يعيد الاتصال أبدًا من تلقاء نفسه:
 * لا يوجد أي Listener/Scheduler يستدعي openAudioConnection().
 */
public final class VoiceComplaintSystem extends ListenerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(VoiceComplaintSystem.class);
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final BotConfig config;
    private final TicketStore store;
    private final Map<Long, VoiceRecordingSession> sessions = new ConcurrentHashMap<>(); // guildId -> session
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("voice-scheduler").daemon(true).factory());

    public VoiceComplaintSystem(BotConfig config, TicketStore store) {
        this.config = config;
        this.store = store;
    }

    // =====================================================================
    // Buttons
    // =====================================================================

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.equals(TicketIds.VOICE_START) && !id.equals(TicketIds.VOICE_STOP)) return;
        if (event.getGuild() == null) return;

        Ticket ticket = store.byChannel(event.getChannel().getIdLong()).orElse(null);
        if (ticket == null || !ticket.type().isVoice()) {
            event.reply("❌ هذه ليست تذكرة شكوى صوتية.").setEphemeral(true).queue();
            return;
        }
        Member member = event.getMember();
        boolean allowed = member != null
                && (member.getIdLong() == ticket.ownerId() || StaffPermissions.isStaff(member, config));
        if (!allowed) {
            event.reply("⛔ فقط صاحب الشكوى أو الإدارة يستطيعون التحكم في التسجيل.").setEphemeral(true).queue();
            return;
        }
        if (ticket.status() != Ticket.Status.OPEN) {
            event.reply("❌ هذه التذكرة مغلقة.").setEphemeral(true).queue();
            return;
        }

        if (id.equals(TicketIds.VOICE_START)) {
            handleStart(event, ticket, member);
        } else {
            handleStop(event, ticket);
        }
    }

    private void handleStart(ButtonInteractionEvent event, Ticket ticket, Member member) {
        // ===== التحقق من نوع التذكرة: التسجيل الصوتي مسموح فقط في "شكوى على إداري" =====
        if (!ticket.type().isVoice()) {
            event.reply("❌ لا يمكن بدء التسجيل! يجب أن يختار العضو «شكوى صوتية» عند فتح التذكرة لتفعيل التسجيل الصوتي.")
                    .setEphemeral(true).queue();
            return;
        }

        Guild guild = event.getGuild();
        GuildVoiceState voiceState = member.getVoiceState();
        AudioChannel channel = voiceState == null ? null : voiceState.getChannel();
        if (channel == null) {
            event.reply("🔇 ادخل روم صوتي أولاً ثم اضغط «بدء التسجيل».").setEphemeral(true).queue();
            return;
        }
        Member self = guild.getSelfMember();
        if (!self.hasPermission(channel, Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT)) {
            event.reply("⛔ البوت لا يملك صلاحية دخول الروم " + channel.getAsMention()).setEphemeral(true).queue();
            return;
        }

        Path file = config.recordingsDir()
                .resolve(ticket.displayName())
                .resolve("recording-" + LocalDateTime.now(config.zone()).format(FILE_TIME) + ".wav");

        VoiceRecordingSession session;
        try {
            session = new VoiceRecordingSession(guild, ticket, channel, member.getIdLong(), file, scheduler,
                    this::requestStop, this::onRecordingStarted);
        } catch (IOException e) {
            LOG.error("Cannot create recording file", e);
            event.reply("❌ تعذر إنشاء ملف التسجيل: " + e.getMessage()).setEphemeral(true).queue();
            return;
        }

        // ===== الحماية من الضغط المزدوج / جلستين بنفس الوقت =====
        VoiceRecordingSession existing = sessions.putIfAbsent(guild.getIdLong(), session);
        if (existing != null) {
            deleteQuietly(file);
            String where = existing.ticket() == ticket ? "في هذه التذكرة" : "في تذكرة أخرى (" + existing.ticket().displayName() + ")";
            event.reply("⏳ يوجد تسجيل جارٍ بالفعل " + where + ".").setEphemeral(true).queue();
            return;
        }

        ticket.setVoiceStatus(Ticket.VoiceStatus.CONNECTING);
        // Edit رسالة التحكم: "جارٍ الاتصال" + تفعيل زر إنهاء التسجيل
        event.editMessageEmbeds(TicketPanel.embed(ticket)).setComponents(TicketPanel.rows(ticket)).queue();

        try {
            session.start(channel, config.maxRecordingMinutes());
        } catch (Exception e) {
            LOG.error("openAudioConnection failed", e);
            requestStop(session, "تعذر فتح الاتصال الصوتي: " + e.getMessage());
        }
    }

    private void handleStop(ButtonInteractionEvent event, Ticket ticket) {
        VoiceRecordingSession session = sessions.get(event.getGuild().getIdLong());
        if (session == null || session.ticket() != ticket) {
            // لا توجد جلسة: نصحح الرسالة فقط (ربما البوت أعيد تشغيله)
            ticket.setVoiceStatus(Ticket.VoiceStatus.IDLE);
            event.editMessageEmbeds(TicketPanel.embed(ticket)).setComponents(TicketPanel.rows(ticket)).queue();
            return;
        }
        event.deferEdit().queue(); // نرد على الـ Interaction، والرسالة ستتحدث بعد الحفظ
        requestStop(session, "تم إنهاء التسجيل بواسطة " + event.getUser().getName());
    }

    // =====================================================================
    // Voice events
    // =====================================================================

    /**
     * Events دخول/خروج الروم.
     * لاحظ: هنا نحن **نوقف** التسجيل فقط عند الحاجة، ولا **نعيد الدخول** أبدًا.
     * (في كثير من البوتات يوجد كود مثل: "لو خرج البوت => ادخل مرة أخرى" وهذا يسبب Loop)
     */
    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        VoiceRecordingSession session = sessions.get(event.getGuild().getIdLong());
        if (session == null || session.state() == VoiceRecordingSession.State.STOPPING) return;

        Member member = event.getMember();
        boolean isSelf = member.getIdLong() == event.getJDA().getSelfUser().getIdLong();
        AudioChannel joined = event.getChannelJoined();

        if (isSelf) {
            // البوت طُرد / نُقل لروم آخر يدويًا
            if (event.getChannelLeft() != null && session.state() == VoiceRecordingSession.State.RECORDING
                    && (joined == null || joined.getIdLong() != session.voiceChannelId())) {
                requestStop(session, "تم فصل البوت من الروم الصوتي");
            }
            return;
        }

        // صاحب الشكوى خرج من روم التسجيل => نوقف ونحفظ
        if (member.getIdLong() == session.ticket().ownerId()
                && event.getChannelLeft() != null
                && event.getChannelLeft().getIdLong() == session.voiceChannelId()) {
            requestStop(session, "صاحب الشكوى غادر الروم الصوتي");
        }
    }

    /** إذا حُذفت قناة التذكرة أثناء التسجيل. */
    @Override
    public void onChannelDelete(ChannelDeleteEvent event) {
        sessions.values().stream()
                .filter(s -> s.ticket().channelId() == event.getChannel().getIdLong())
                .findFirst()
                .ifPresent(s -> requestStop(s, "تم حذف قناة التذكرة"));
    }

    // =====================================================================
    // Start / Stop
    // =====================================================================

    private void onRecordingStarted(VoiceRecordingSession session) {
        Ticket ticket = session.ticket();
        ticket.setVoiceStatus(Ticket.VoiceStatus.RECORDING);
        TicketPanel.refresh(guild(session), ticket);
    }

    /** يمكن استدعاؤها من أي مكان وأي عدد من المرات بأمان. */
    public CompletableFuture<Void> requestStop(VoiceRecordingSession session, String reason) {
        return session.pipelineOnce(() -> buildStopPipeline(session, reason));
    }

    private CompletableFuture<Void> buildStopPipeline(VoiceRecordingSession session, String reason) {
        Ticket ticket = session.ticket();
        ticket.setVoiceStatus(Ticket.VoiceStatus.SAVING);
        TicketPanel.refresh(guild(session), ticket);
        return session.stop(reason)
                .thenCompose(result -> uploadRecording(session, result))
                .exceptionally(err -> {
                    LOG.error("Failed to finish recording for {}", ticket.displayName(), err);
                    return null;
                })
                .whenComplete((v, e) -> {
                    // التنظيف النهائي: إزالة الجلسة => يسمح ببدء تسجيل جديد لاحقًا
                    if (sessions.remove(session.guildId(), session)) {
                        ticket.setVoiceStatus(Ticket.VoiceStatus.IDLE);
                        store.save();
                        TicketPanel.refresh(guild(session), ticket);
                        LOG.info("[Voice] Session cleaned up for {}", ticket.displayName());
                    }
                });
    }

    /** إيقاف التسجيل الخاص بتذكرة معينة (يستخدمه زر الإغلاق قبل الحفظ). */
    public CompletableFuture<Void> stopForTicket(Ticket ticket, String reason) {
        VoiceRecordingSession session = sessions.get(ticket.guildId());
        if (session == null || session.ticket() != ticket) {
            return CompletableFuture.completedFuture(null);
        }
        return requestStop(session, reason);
    }

    private CompletableFuture<Void> uploadRecording(VoiceRecordingSession session, VoiceRecordingSession.Result result) {
        Ticket ticket = session.ticket();
        Guild guild = guild(session);
        TextChannel channel = guild == null ? null : guild.getTextChannelById(ticket.channelId());

        if (!result.hasAudio()) {
            deleteQuietly(result.file());
            if (channel != null) {
                return channel.sendMessage("🎙️ انتهى التسجيل بدون صوت مسجل. السبب: " + result.stopReason())
                        .submit().handle((m, e) -> null);
            }
            return CompletableFuture.completedFuture(null);
        }

        ticket.incrementRecordings();
        if (channel == null) return CompletableFuture.completedFuture(null);

        long size;
        try {
            size = Files.size(result.file());
        } catch (IOException e) {
            size = Long.MAX_VALUE;
        }
        String text = "🎙️ **تسجيل الشكوى الصوتية** — المدة: `%s`\nبدأه: <@%d> • سبب الإيقاف: %s"
                .formatted(format(result.duration()), session.startedById(), result.stopReason());

        // ملف التسجيل يُرسل داخل التذكرة كدليل => سيظهر تلقائيًا في الـ Transcript عند الحفظ
        if (size <= guild.getMaxFileSize()) {
            return channel.sendMessage(text)
                    .addFiles(FileUpload.fromData(result.file().toFile()))
                    .submit().handle((m, e) -> null);
        }
        return channel.sendMessage(text + "\n⚠️ الملف أكبر من حد الرفع في Discord، تم حفظه على السيرفر:\n`"
                        + result.file().toAbsolutePath() + "`")
                .submit().handle((m, e) -> null);
    }

    // =====================================================================
    // Shutdown
    // =====================================================================

    /** عند إيقاف البوت: إنهاء كل التسجيلات وحفظ الملفات محليًا. */
    public void shutdown() {
        for (VoiceRecordingSession s : sessions.values()) {
            try {
                s.stop("إيقاف البوت").get(15, TimeUnit.SECONDS);
            } catch (Exception e) {
                LOG.warn("Could not finish recording on shutdown", e);
            }
        }
        sessions.clear();
        scheduler.shutdownNow();
    }

    private Guild guild(VoiceRecordingSession session) {
        JDA jda = jdaRef;
        return jda == null ? null : jda.getGuildById(session.guildId());
    }

    private volatile JDA jdaRef;

    /** يُستدعى من Main بعد بناء JDA. */
    public void setJda(JDA jda) {
        this.jdaRef = jda;
    }

    private static String format(Duration d) {
        return "%02d:%02d".formatted(d.toMinutes(), d.toSecondsPart());
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // لا يهم
        }
    }
}
