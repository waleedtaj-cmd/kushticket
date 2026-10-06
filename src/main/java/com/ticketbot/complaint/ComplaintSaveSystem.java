package com.ticketbot.complaint;

import com.ticketbot.config.BotConfig;
import com.ticketbot.ticket.StaffPermissions;
import com.ticketbot.ticket.Ticket;
import com.ticketbot.ticket.TicketIds;
import com.ticketbot.ticket.TicketPanel;
import com.ticketbot.ticket.TicketStore;
import com.ticketbot.transcript.TranscriptCollector;
import com.ticketbot.transcript.TranscriptModels.TranscriptDocument;
import com.ticketbot.transcript.TranscriptModels.TranscriptMessage;
import com.ticketbot.transcript.TranscriptWriter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * زر "حفظ الشكوى" (ticket_save).
 *
 * الخطوات:
 *   1) التأكد أن الضاغط إداري.
 *   2) جلب كل رسائل القناة (مع Pagination) => TranscriptCollector.
 *   3) لو لم يتغير شيء منذ آخر حفظ => لا ننشئ نسخة جديدة (منع التكرار).
 *   4) تحميل المرفقات محليًا.
 *   5) كتابة transcript.txt + transcript.html + complaint.json في:
 *        data/transcripts/<channel>-<channelId>/v001/
 *   6) تحديث بيانات التذكرة + تعديل رسالة التحكم (Edit) لتظهر "آخر حفظ".
 *   7) (اختياري) إرسال نسخة لقناة الأرشيف transcript.channel.id.
 */
public final class ComplaintSaveSystem extends ListenerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(ComplaintSaveSystem.class);

    /** نتيجة الحفظ. */
    public record SaveResult(boolean created, int version, Path folder, int messageCount, int attachmentCount) {}

    private final BotConfig config;
    private final TicketStore store;
    private final TranscriptWriter writer;

    /** عمليات الحفظ الجارية لكل قناة. يمنع تشغيل عمليتي حفظ بنفس الوقت لنفس التذكرة. */
    private final Map<Long, CompletableFuture<SaveResult>> inFlight = new ConcurrentHashMap<>();

    /** Java 21 Virtual Threads: لكتابة الملفات بدون حجز Threads الخاصة بـ JDA. */
    private final ExecutorService io = Executors.newVirtualThreadPerTaskExecutor();

    public ComplaintSaveSystem(BotConfig config, TicketStore store) {
        this.config = config;
        this.store = store;
        this.writer = new TranscriptWriter(config.zone());
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!event.getComponentId().equals(TicketIds.SAVE)) return;

        Ticket ticket = store.byChannel(event.getChannel().getIdLong()).orElse(null);
        if (ticket == null || !(event.getChannel() instanceof TextChannel channel)) {
            event.reply("❌ هذه القناة ليست تذكرة مسجلة.").setEphemeral(true).queue();
            return;
        }
        Member member = event.getMember();
        if (!StaffPermissions.isStaff(member, config)) {
            event.reply(StaffPermissions.NO_PERMISSION).setEphemeral(true).queue();
            return;
        }
        if (isSaving(channel.getIdLong())) {
            event.reply("⏳ يتم حفظ هذه الشكوى الآن، انتظر لحظات.").setEphemeral(true).queue();
            return;
        }

        // deferReply: الحفظ قد يأخذ أكثر من 3 ثوانٍ (حد Discord للرد على الـ Interaction).
        // true = الرد Ephemeral (يظهر للإداري فقط) => لا رسائل إضافية في التذكرة.
        event.deferReply(true).queue();
        InteractionHook hook = event.getHook();

        saveTicket(ticket, channel, member.getUser()).whenComplete((result, error) -> {
            if (error != null) {
                Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                LOG.error("Save failed for {}", ticket.displayName(), cause);
                hook.editOriginal("❌ فشل حفظ الشكوى: " + cause.getMessage()).queue();
                return;
            }
            if (!result.created()) {
                hook.editOriginal("ℹ️ لا توجد رسائل أو تغييرات جديدة منذ آخر حفظ.\nآخر نسخة محفوظة: `v%03d`"
                        .formatted(result.version())).queue();
                return;
            }
            // رسالة للإداري فقط (Ephemeral) بدون إرفاق ملفات — الملفات محفوظة محليًا ويمكن الوصول إليها عبر رابط الويب.
            String text = """
                    ✅ تم حفظ الشكوى بالكامل.
                    • النسخة: `v%03d`
                    • عدد الرسائل: %d
                    • عدد المرفقات: %d
                    • المسار: `%s`""".formatted(result.version(), result.messageCount(),
                    result.attachmentCount(), result.folder().toAbsolutePath());
            hook.editOriginal(text).queue();
        });
    }

    public boolean isSaving(long channelId) {
        return inFlight.containsKey(channelId);
    }

    /**
     * يحفظ التذكرة. إذا كان هناك حفظ جارٍ لنفس التذكرة، ينتظر انتهاءه ثم يبدأ (لا يعمل بالتوازي).
     * يُستخدم من زر الحفظ ومن زر الإغلاق (حفظ تلقائي قبل حذف القناة).
     */
    public CompletableFuture<SaveResult> saveTicket(Ticket ticket, TextChannel channel, User savedBy) {
        long key = channel.getIdLong();
        CompletableFuture<SaveResult> mine = inFlight.compute(key, (k, previous) -> previous == null
                ? doSave(ticket, channel, savedBy)
                : previous.handle((r, e) -> null).thenCompose(x -> doSave(ticket, channel, savedBy)));
        mine.whenComplete((r, e) -> inFlight.remove(key, mine));
        return mine;
    }

    private CompletableFuture<SaveResult> doSave(Ticket ticket, TextChannel channel, User savedBy) {
        return TranscriptCollector.fetchAllMessages(channel).thenCompose(messages -> {
            long newestId = messages.isEmpty() ? 0 : messages.getLast().getIdLong();

            // ===== منع النسخ المكررة =====
            if (!ticket.hasChangesSinceLastSave(newestId)) {
                return CompletableFuture.completedFuture(new SaveResult(false, ticket.savedVersion(),
                        Path.of(ticket.lastSavedPath() == null ? "" : ticket.lastSavedPath()), messages.size(), 0));
            }

            int version = ticket.nextSaveVersion();
            Path ticketFolder = config.transcriptsDir().resolve(
                    TranscriptCollector.sanitize(channel.getName()) + "-" + channel.getId());
            Path versionFolder = ticketFolder.resolve("v%03d".formatted(version));

            CompletableFuture<List<TranscriptMessage>> converted =
                    TranscriptCollector.convertAndDownload(messages, versionFolder, config.maxAttachmentDownloadBytes());
            CompletableFuture<String> ownerName = userName(channel.getJDA(), ticket.ownerId(), messages);
            CompletableFuture<String> claimerName = userName(channel.getJDA(), ticket.claimedById(), messages);
            CompletableFuture<String> closerName = userName(channel.getJDA(), ticket.closedById(), messages);

            return CompletableFuture.allOf(converted, ownerName, claimerName, closerName).thenApplyAsync(v -> {
                Instant savedAt = Instant.now();
                Guild guild = channel.getGuild();
                TranscriptDocument doc = new TranscriptDocument(
                        ticket.displayName(),
                        ticket.number(),
                        version,
                        guild.getName(),
                        guild.getId(),
                        channel.getName(),
                        channel.getId(),
                        Long.toUnsignedString(ticket.ownerId()),
                        ownerName.join(),
                        ticket.type().arabicLabel(),
                        ticket.type().englishLabel(),
                        ticket.target(),
                        ticket.reason(),
                        ticket.evidence(),
                        ticket.claimedById() == 0 ? null : Long.toUnsignedString(ticket.claimedById()),
                        claimerName.join(),
                        ticket.status() == Ticket.Status.SOLVED ? "Solved / تم الحل" : "Open / مفتوحة",
                        ticket.closedById() == 0 ? null : Long.toUnsignedString(ticket.closedById()),
                        closerName.join(),
                        ticket.createdAt(),
                        savedAt,
                        savedBy.getId(),
                        savedBy.getName(),
                        converted.join());
                try {
                    writer.writeAll(doc, versionFolder);
                } catch (IOException e) {
                    throw new CompletionException(e);
                }

                ticket.markSaved(version, newestId, savedBy.getIdLong(), savedAt, versionFolder.toString());
                store.save();
                TicketPanel.refresh(guild, ticket); // Edit رسالة التحكم: "آخر حفظ v00X"
                sendToArchive(guild, ticket, doc, versionFolder);

                LOG.info("Saved transcript {} v{} ({} messages) to {}", ticket.displayName(), version,
                        doc.messages().size(), versionFolder.toAbsolutePath());
                return new SaveResult(true, version, versionFolder, doc.messages().size(), doc.attachmentCount());
            }, io);
        });
    }

    /** اسم المستخدم: من الرسائل أولاً (بدون طلب API)، ثم من Discord. */
    private static CompletableFuture<String> userName(JDA jda, long userId, List<Message> messages) {
        if (userId == 0) return CompletableFuture.completedFuture(null);
        for (Message m : messages) {
            if (m.getAuthor().getIdLong() == userId) {
                return CompletableFuture.completedFuture(m.getAuthor().getName());
            }
        }
        return jda.retrieveUserById(userId).submit()
                .thenApply(User::getName)
                .exceptionally(err -> Long.toUnsignedString(userId));
    }

    /**
     * إرسال نسخة إلى قناة الأرشيف مع زر رابط مباشر للمتصفح.
     *
     * ملاحظة: لا نرفع ملفات Transcript نفسها على Discord.
     * بدلًا من ذلك، نرسل Embed مع زر Link Button يفتح صفحة ويب تعرض الـ Transcript.
     * الرابط يُبنى من Environment Variable (RAILWAY_PUBLIC_DOMAIN أو localhost).
     */
    private void sendToArchive(Guild guild, Ticket ticket, TranscriptDocument doc, Path folder) {
        if (config.transcriptChannelId() == 0) return;
        TextChannel archive = guild.getTextChannelById(config.transcriptChannelId());
        if (archive == null) {
            LOG.warn("transcript.channel.id={} not found", config.transcriptChannelId());
            return;
        }

        // 1. جلب نطاق الاستضافة من Environment Variable (Railway / Vercel / محلي)
        String domain = System.getenv("RAILWAY_PUBLIC_DOMAIN");
        if (domain == null || domain.isEmpty()) {
            domain = System.getenv("VERCEL_URL");
        }
        if (domain == null || domain.isEmpty()) {
            domain = "localhost:3000";
        }
        // VERCEL_URL قد يأتي بدون https:// — نضيفه
        String baseUrl = domain.startsWith("http") ? domain : "https://" + domain;

        // 2. اسم مجلد التذكرة (مثل complaint-admin-0001-<channelId>) لتوليد الرابط المباشر
        String folderName = folder.getParent() != null ? folder.getParent().getFileName().toString() : folder.getFileName().toString();
        String webUrl = baseUrl + "/api/transcript?ticket=" + folderName + "&v=" + doc.version();

        var embed = new EmbedBuilder()
                .setTitle("💾 Transcript — " + ticket.displayName() + " (v%03d)".formatted(doc.version()))
                .addField("صاحب الشكوى", "<@" + ticket.ownerId() + ">", true)
                .addField("النوع", ticket.type().arabicLabel(), true)
                .addField("Claimed by", ticket.claimedById() == 0 ? "لا يوجد" : "<@" + ticket.claimedById() + ">", true)
                .addField("الحالة", doc.status(), true)
                .addField("حفظ بواسطة", "<@" + doc.savedById() + ">", true)
                .addField("الرسائل / المرفقات", doc.messages().size() + " / " + doc.attachmentCount(), true)
                .setTimestamp(doc.savedAt())
                .build();

        // 3. زر تفاعلي يفتح رابط الـ Transcript في المتصفح (Link Button)
        Button viewButton = Button.link(webUrl, "عرض الشكوى 🌐");

        // 4. إرسال الـ Embed + الزر إلى قناة الأرشيف
        archive.sendMessageEmbeds(embed)
                .setComponents(ActionRow.of(viewButton))
                .queue(null, err -> LOG.warn("Archive upload failed: {}", err.getMessage()));
    }

    public void shutdown() {
        io.shutdown();
    }
}
