package com.ticketbot;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audio.AudioReceiveHandler;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.audio.CombinedAudio;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;
import net.dv8tion.jda.api.utils.FileUpload;

public class TicketSystem extends ListenerAdapter {

    private static final long TICKET_CATEGORY_ID = 1537467402936123473L;
    private static final long ADMIN_HELP_CHANNEL_ID = 1537468070497493143L;
    private static final long VOICE_ACCESS_ROLE_ID = 1552371226046111784L;
    private static final long LOG_CHANNEL_ID = 1537847355552964618L;
    private static final long HELP_VOICE_CHANNEL_ID = 1537470982082662491L;

    private static final List<Long> GENERAL_STAFF_ROLE_IDS = Arrays.asList(
            1534802503353241622L,
            1534802509854674965L
    );

    private static final List<Long> HIGH_STAFF_ROLE_IDS = Arrays.asList(
            1542590923668725880L,
            1542590572903010324L,
            1534802512765255690L
    );

    private static final Map<Long, VoiceRecorderHandler> activeRecorders = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    @Override
    public void onReady(ReadyEvent event) {
        TextChannel helpChannel = event.getJDA().getTextChannelById(ADMIN_HELP_CHANNEL_ID);
        if (helpChannel != null) {
            helpChannel.getHistory().retrievePast(5).queue(messages -> {
                boolean embedExists = messages.stream().anyMatch(m -> 
                    !m.getEmbeds().isEmpty() && 
                    m.getEmbeds().get(0).getTitle() != null && 
                    m.getEmbeds().get(0).getTitle().contains("نظام الدعم الفني")
                );

                if (!embedExists) {
                    sendTicketPanel(helpChannel);
                }
            });
        }
    }

    private void sendTicketPanel(TextChannel channel) {
        EmbedBuilder mainEmbed = new EmbedBuilder()
                .setTitle("🎫 نظام الدعم الفني والشكاوى | Ticket Support")
                .setDescription("مرحباً بك في نظام الدعم الفني الخاص بالسيرفر!\n\n" +
                        "الرجاء اختيار نوع الخدمة المناسبة بالضغط على أحد الأزرار أدناه:\n\n" +
                        "📩 **تذكرة دعم عامة:** للاستفسارات والمشاكل العامة.\n" +
                        "⚠️ **شكوى على إداري:** للشكاوى السرية الخاصة بكادر الإدارة.")
                .setColor(Color.CYAN)
                .setFooter("إدارة السيرفر", channel.getGuild().getIconUrl())
                .setTimestamp(Instant.now());

        Button createNormalTicket = Button.primary("create_ticket:normal", "📩 فتح تذكرة دعم عامة");
        Button createStaffReport = Button.danger("create_ticket:staff_report", "⚠️ شكوى على إداري");

        channel.sendMessageEmbeds(mainEmbed.build())
                .setActionRow(createNormalTicket, createStaffReport)
                .queue();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();

        if (componentId.startsWith("create_ticket")) {
            handleCreateTicket(event);
        } else if (componentId.startsWith("voice_request:")) {
            handleVoiceRequestAndRecord(event);
        } else if (componentId.startsWith("problem_solved:")) {
            handleProblemSolved(event);
        } else if (componentId.startsWith("save_ticket:")) {
            handleSaveTicketClick(event);
        } else if (componentId.startsWith("stop_record:")) {
            handleStopRecord(event);
        } else if (componentId.startsWith("quick_mute:")) {
            handleQuickMute(event);
        } else if (componentId.startsWith("quick_ban:")) {
            handleQuickBan(event);
        }
    }

    private void handleCreateTicket(ButtonInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) return;

        Category category = guild.getCategoryById(TICKET_CATEGORY_ID);
        boolean isStaffReport = event.getComponentId().endsWith(":staff_report");

        String prefix = isStaffReport ? "شكوى-إداري" : "تكت-عادي";
        Member selfMember = guild.getSelfMember();

        ChannelAction<TextChannel> channelAction = guild.createTextChannel(prefix + "-" + member.getEffectiveName(), category)
                .addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
                .addPermissionOverride(selfMember, EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_ATTACH_FILES, Permission.MESSAGE_EMBED_LINKS, Permission.MANAGE_CHANNEL), null)
                .addPermissionOverride(member, EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_ATTACH_FILES, Permission.MESSAGE_EMBED_LINKS), null);

        List<Long> targetRoles = isStaffReport ? HIGH_STAFF_ROLE_IDS : GENERAL_STAFF_ROLE_IDS;
        for (Long roleId : targetRoles) {
            Role role = guild.getRoleById(roleId);
            if (role != null) {
                channelAction = channelAction.addPermissionOverride(role, EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_ATTACH_FILES, Permission.MESSAGE_EMBED_LINKS), null);
            }
        }

        channelAction.queue(channel -> {
            event.reply("تم إنشاء التذكرة بنجاح: " + channel.getAsMention()).setEphemeral(true).queue();

            EmbedBuilder embed = new EmbedBuilder();
            Button voiceBtn = Button.primary("voice_request:" + member.getId(), "طلب شكوى صوتية 🎧");
            Button solvedBtn = Button.success("problem_solved:" + member.getId(), "تم حل المشكلة ✅");
            Button saveBtn = Button.secondary("save_ticket:" + member.getId(), "حفظ الشكوى 💾");

            embed.setTitle(isStaffReport ? "⚠️ شكوى على إداري" : "📩 تذكرة الدعم الفني")
                 .setDescription("مرحباً بك " + member.getAsMention() + "!\n" +
                         (isStaffReport ? "إذا كانت الشكوى تستدعي تسجيلاً صوتياً، يرجى التواجد في روم الـ Help والضغط على زر **طلب شكوى صوتية 🎧** لدخول البوت وبدء التسجيل فوراً." 
                                       : "يرجى كتابة التفاصيل وسيتم الرد عليك قريباً."))
                 .setColor(isStaffReport ? Color.RED : Color.BLUE);

            channel.sendMessageEmbeds(embed.build()).setActionRow(voiceBtn, solvedBtn, saveBtn).queue();
        });
    }

    private void handleVoiceRequestAndRecord(ButtonInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) return;

        event.deferReply().queue();

        Role voiceRole = guild.getRoleById(VOICE_ACCESS_ROLE_ID);
        if (voiceRole != null) {
            guild.addRoleToMember(member, voiceRole).queue();
        }

        VoiceChannel helpVoiceChannel = guild.getVoiceChannelById(HELP_VOICE_CHANNEL_ID);

        if (helpVoiceChannel == null) {
            event.getHook().sendMessage("❌ لم يتم العثور على روم الـ Help الصوتي. تأكد من إعداد HELP_VOICE_CHANNEL_ID.").queue();
            return;
        }

        if (member.getVoiceState() == null || !member.getVoiceState().inAudioChannel() || member.getVoiceState().getChannel().getIdLong() != HELP_VOICE_CHANNEL_ID) {
            event.getHook().sendMessage("⚠️ لازم تكون موجود داخل روم الـ Help الصوتي " + helpVoiceChannel.getAsMention() + " عشان البوت يدخل يسجل معاك!").queue();
            return;
        }

        AudioManager audioManager = guild.getAudioManager();

        if (audioManager.isConnected()) {
            audioManager.closeAudioConnection();
            activeRecorders.remove(guild.getIdLong());
        }

        startVoiceRecording(guild, helpVoiceChannel, event.getChannel().asTextChannel(), member);

        EmbedBuilder recEmbed = new EmbedBuilder()
                .setTitle("🚨 جارٍ التسجيل الصوتي الآن")
                .setDescription("🔴 **انضم البوت إلى:** " + helpVoiceChannel.getAsMention() + "\n" +
                        "👤 **صاحب الشكوى:** " + member.getAsMention() + "\n" +
                        "⏱️ **مدة التسجيل القصوى:** 10 دقائق تلقائياً.\n" +
                        "🔒 عند الانتهاء، سيتم توثيق المقطع وإرساله للإدارة العليا.")
                .setColor(Color.RED)
                .setTimestamp(Instant.now());

        Button stopRecBtn = Button.danger("stop_record:" + member.getId(), "⏹️ إنهاء وتوثيق التسجيل");

        event.getHook().sendMessageEmbeds(recEmbed.build()).setActionRow(stopRecBtn).queue();
    }

    private void startVoiceRecording(Guild guild, VoiceChannel voiceChannel, TextChannel ticketChannel, Member reporter) {
        AudioManager audioManager = guild.getAudioManager();

        VoiceRecorderHandler recorderHandler = new VoiceRecorderHandler(voiceChannel);
        activeRecorders.put(guild.getIdLong(), recorderHandler);

        audioManager.setSendingHandler(new SilenceAudioHandler());
        audioManager.setReceivingHandler(recorderHandler);

        audioManager.setSelfDeafened(false);
        audioManager.setSelfMuted(false);

        audioManager.openAudioConnection(voiceChannel);

        scheduler.schedule(() -> {
            if (activeRecorders.containsKey(guild.getIdLong())) {
                stopAndSendRecordingWithHook(guild, ticketChannel, reporter, "وصل التسجيل للحد الأقصى (10 دقائق)", null);
            }
        }, 10, TimeUnit.MINUTES);
    }

    private void handleStopRecord(ButtonInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) return;

        if (!checkIsStaff(member)) {
            event.reply("عذراً، فقط الإدارة يمكنها إيقاف وتوثيق التسجيل.").setEphemeral(true).queue();
            return;
        }

        event.deferReply().queue(hook -> {
            stopAndSendRecordingWithHook(guild, event.getChannel().asTextChannel(), member, "تم إيقاف التسجيل يدوياً بواسطة " + member.getAsMention(), hook);
        });
    }

    private void stopAndSendRecordingWithHook(Guild guild, TextChannel ticketChannel, Member reporter, String reason, InteractionHook hook) {
        VoiceRecorderHandler recorder = activeRecorders.get(guild.getIdLong());
        AudioManager audioManager = guild.getAudioManager();

        Runnable sendFailMsg = () -> {
            if (hook != null) hook.sendMessage("❌ تعذر العثور على جلسة تسجيل نشطة!").queue();
            else ticketChannel.sendMessage("❌ تعذر العثور على جلسة تسجيل نشطة!").queue();
        };

        if (recorder == null) {
            sendFailMsg.run();
            return;
        }

        activeRecorders.remove(guild.getIdLong());

        if (audioManager.isConnected()) {
            audioManager.closeAudioConnection();
        }

        byte[] audioData = recorder.getAudioData();
        TextChannel logChannel = guild.getTextChannelById(LOG_CHANNEL_ID);

        if (logChannel == null) {
            if (hook != null) hook.sendMessage("❌ روم اللوجات غير موجود! تأكد من LOG_CHANNEL_ID.").queue();
            else ticketChannel.sendMessage("❌ روم اللوجات غير موجود!").queue();
            return;
        }

        if (audioData.length <= 44) { 
            if (hook != null) hook.sendMessage("⚠️ لم يتم تسجيل أي صوت في هذه الجلسة.").queue();
            else ticketChannel.sendMessage("⚠️ لم يتم تسجيل أي صوت في هذه الجلسة.").queue();
            return;
        }

        int maxBytes = 7 * 1024 * 1024;
        if (audioData.length > maxBytes) {
            byte[] trimmedData = new byte[maxBytes];
            System.arraycopy(audioData, 0, trimmedData, 0, maxBytes);
            audioData = trimmedData;
        }

        StringBuilder membersList = new StringBuilder();
        for (Member m : recorder.getAttendees()) {
            membersList.append("• ").append(m.getAsMention()).append(" (ID: `").append(m.getId()).append("`)\n");
        }

        EmbedBuilder logEmbed = new EmbedBuilder()
                .setTitle("🚨 دليل صوتي موثق | Help Voice Record")
                .addField("👤 صاحب البلاغ:", reporter.getAsMention(), true)
                .addField("🎙️ الروم الصوتي:", recorder.getVoiceChannel().getName(), true)
                .addField("📝 سبب الإنهاء:", reason, false)
                .addField("👥 قائمة الحضور أثناء التسجيل:", membersList.toString().isEmpty() ? "لا يوجد" : membersList.toString(), false)
                .setColor(Color.DARK_GRAY)
                .setTimestamp(Instant.now());

        Button muteBtn = Button.danger("quick_mute:" + reporter.getId(), "🔇 ميوت لصاحب البلاغ");
        Button banBtn = Button.danger("quick_ban:" + reporter.getId(), "🔨 بان سريع");

        FileUpload fileUpload = FileUpload.fromData(audioData, "Help_Voice_Evidence_" + System.currentTimeMillis() + ".wav");

        logChannel.sendMessageEmbeds(logEmbed.build())
                  .addFiles(fileUpload)
                  .setActionRow(muteBtn, banBtn)
                  .queue(
                      success -> {
                          if (hook != null) hook.sendMessage("✅ تم إرسال وتوثيق التسجيل بنجاح في روم اللوجات.").queue();
                          else ticketChannel.sendMessage("✅ تم إرسال وتوثيق التسجيل بنجاح في روم اللوجات.").queue();
                      },
                      error -> {
                          if (hook != null) hook.sendMessage("❌ فشل إرسال التسجيل إلى اللوجات: " + error.getMessage()).queue();
                          else ticketChannel.sendMessage("❌ فشل إرسال التسجيل إلى اللوجات: " + error.getMessage()).queue();
                      }
                  );
    }

    private void handleQuickMute(ButtonInteractionEvent event) {
        if (!checkIsStaff(event.getMember())) return;
        String targetId = event.getComponentId().split(":")[1];
        event.getGuild().retrieveMemberById(targetId).queue(target -> {
            target.mute(true).queue(
                s -> event.reply("✅ تم إعطاء ميوت صوتي للعضو " + target.getAsMention()).setEphemeral(true).queue(),
                e -> event.reply("❌ فشل إعطاء الميوت.").setEphemeral(true).queue()
            );
        });
    }

    private void handleQuickBan(ButtonInteractionEvent event) {
        if (!checkIsStaff(event.getMember())) return;
        String targetId = event.getComponentId().split(":")[1];
        event.getGuild().retrieveMemberById(targetId).queue(target -> {
            target.ban(0, TimeUnit.DAYS).reason("بناءً على الدليل الصوتي الموثق").queue(
                s -> event.reply("✅ تم حظر العضو بنجاح " + target.getAsMention()).setEphemeral(true).queue(),
                e -> event.reply("❌ فشل حظر العضو.").setEphemeral(true).queue()
            );
        });
    }

    private static class SilenceAudioHandler implements AudioSendHandler {
        private final byte[] silenceBytes = new byte[3840]; 

        @Override
        public boolean canProvide() {
            return true;
        }

        @Override
        public ByteBuffer provide20MsAudio() {
            return ByteBuffer.wrap(silenceBytes);
        }

        @Override
        public boolean isOpus() {
            return false;
        }
    }

    private static class VoiceRecorderHandler implements AudioReceiveHandler {
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        private final Set<Member> attendees = new HashSet<>();
        private final VoiceChannel voiceChannel;

        public VoiceRecorderHandler(VoiceChannel channel) {
            this.voiceChannel = channel;
            this.attendees.addAll(channel.getMembers());
            writeWavHeaderPlaceholder();
        }

        private void writeWavHeaderPlaceholder() {
            try {
                outputStream.write(new byte[44]); 
            } catch (IOException ignored) {}
        }

        @Override
        public boolean canReceiveCombined() {
            return true;
        }

        @Override
        public boolean canReceiveUser() {
            return false;
        }

        @Override
        public synchronized void handleCombinedAudio(CombinedAudio combinedAudio) {
            byte[] data = combinedAudio.getAudioData(1.0);
            if (data != null && data.length > 0) {
                try {
                    outputStream.write(data);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public synchronized byte[] getAudioData() {
            byte[] rawPcm = outputStream.toByteArray();
            if (rawPcm.length <= 44) return new byte[0];

            int pcmDataLength = rawPcm.length - 44;
            long totalDataLen = pcmDataLength + 36;
            long longSampleRate = 48000;
            int channels = 2;
            long byteRate = 16 * longSampleRate * channels / 8;

            byte[] header = new byte[44];
            header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
            header[4] = (byte) (totalDataLen & 0xff);
            header[5] = (byte) ((totalDataLen >> 8) & 0xff);
            header[6] = (byte) ((totalDataLen >> 16) & 0xff);
            header[7] = (byte) ((totalDataLen >> 24) & 0xff);
            header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
            header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
            header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0;
            header[20] = 1; header[21] = 0;
            header[22] = (byte) channels; header[23] = 0;
            header[24] = (byte) (longSampleRate & 0xff);
            header[25] = (byte) ((longSampleRate >> 8) & 0xff);
            header[26] = (byte) ((longSampleRate >> 16) & 0xff);
            header[27] = (byte) ((longSampleRate >> 24) & 0xff);
            header[28] = (byte) (byteRate & 0xff);
            header[29] = (byte) ((byteRate >> 8) & 0xff);
            header[30] = (byte) ((byteRate >> 16) & 0xff);
            header[31] = (byte) ((byteRate >> 24) & 0xff);
            header[32] = (byte) (2 * 16 / 8); header[33] = 0;
            header[34] = 16; header[35] = 0;
            header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
            header[40] = (byte) (pcmDataLength & 0xff);
            header[41] = (byte) ((pcmDataLength >> 8) & 0xff);
            header[42] = (byte) ((pcmDataLength >> 16) & 0xff);
            header[43] = (byte) ((pcmDataLength >> 24) & 0xff);

            System.arraycopy(header, 0, rawPcm, 0, 44);
            return rawPcm;
        }

        public Set<Member> getAttendees() {
            return attendees;
        }

        public VoiceChannel getVoiceChannel() {
            return voiceChannel;
        }
    }

    private void handleSaveTicketClick(ButtonInteractionEvent event) {
        Member clicker = event.getMember();
        if (clicker == null || !checkIsStaff(clicker)) {
            event.reply("عذراً، فقط الإدارة يمكنها حفظ الشكاوى.").setEphemeral(true).queue();
            return;
        }

        String ownerId = event.getComponentId().split(":")[1];
        TextInput detailsInput = TextInput.create("ticket_notes", "تفاصيل/نتائج الشكوى", TextInputStyle.PARAGRAPH)
                .setPlaceholder("اكتب الملاحظات الهامة...").setRequired(true).build();

        Modal modal = Modal.create("save_modal:" + ownerId, "حفظ وتوثيق الشكوى")
                .addActionRow(detailsInput).build();

        event.replyModal(modal).queue();
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (!event.getModalId().startsWith("save_modal:")) return;

        Guild guild = event.getGuild();
        Member admin = event.getMember();
        TextChannel channel = event.getChannel().asTextChannel();
        if (guild == null || admin == null || channel == null) return;

        String ownerId = event.getModalId().split(":")[1];
        String adminNotes = event.getValue("ticket_notes").getAsString();

        event.deferReply().setEphemeral(true).queue();
        TextChannel logChannel = guild.getTextChannelById(LOG_CHANNEL_ID);

        if (logChannel == null || !guild.getSelfMember().hasPermission(logChannel, Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND)) {
            event.getHook().sendMessage("❌ البوت لا يمتلك صلاحية في روم السجلات!").queue();
            return;
        }

        channel.getHistory().retrievePast(50).queue(messages -> {
            StringBuilder chatSummary = new StringBuilder();
            for (int i = messages.size() - 1; i >= 0; i--) {
                Message msg = messages.get(i);
                if (!msg.getAuthor().isBot() && !msg.getContentRaw().isEmpty()) {
                    chatSummary.append("**").append(msg.getAuthor().getEffectiveName()).append("**: ").append(msg.getContentRaw()).append("\n");
                }
            }

            EmbedBuilder logEmbed = new EmbedBuilder()
                    .setTitle("📜 توثيق تذكرة محفوظة")
                    .addField("👤 صاحب التذكرة:", "<@" + ownerId + ">", true)
                    .addField("🛡️ الإداري المحفظ:", admin.getAsMention(), true)
                    .addField("✍️ التفاصيل:", adminNotes, false)
                    .addField("💬 المحادثة:", chatSummary.length() > 0 ? chatSummary.toString() : "لا توجد رسائل", false)
                    .setColor(Color.GREEN)
                    .setTimestamp(Instant.now());

            logChannel.sendMessageEmbeds(logEmbed.build()).queue(s -> event.getHook().sendMessage("✅ تم حفظ الشكوى بنجاح.").queue());
        });
    }

    private void handleProblemSolved(ButtonInteractionEvent event) {
        Guild guild = event.getGuild();
        Member clicker = event.getMember();
        if (guild == null || clicker == null || !checkIsStaff(clicker)) {
            event.reply("عذراً، فقط فريق الإدارة يمكنه إنهاء التذكرة.").setEphemeral(true).queue();
            return;
        }

        if (activeRecorders.containsKey(guild.getIdLong())) {
            stopAndSendRecordingWithHook(guild, event.getChannel().asTextChannel(), clicker, "تم إغلاق التذكرة بواسطة الإدارة", null);
        }

        String targetMemberId = event.getComponentId().split(":")[1];
        Role voiceRole = guild.getRoleById(VOICE_ACCESS_ROLE_ID);
        if (voiceRole != null) {
            guild.retrieveMemberById(targetMemberId).queue(target -> guild.removeRoleFromMember(target, voiceRole).queue(), t -> {});
        }

        event.reply("تم حل المشكلة ✅ وسيتم حذف الروم خلال 5 ثوانٍ...").queue();
        event.getChannel().delete().queueAfter(5, TimeUnit.SECONDS);
    }

    private boolean checkIsStaff(Member member) {
        if (member == null) return false;
        if (member.hasPermission(Permission.ADMINISTRATOR)) return true;
        for (Role memberRole : member.getRoles()) {
            if (GENERAL_STAFF_ROLE_IDS.contains(memberRole.getIdLong()) || HIGH_STAFF_ROLE_IDS.contains(memberRole.getIdLong())) {
                return true;
            }
        }
        return false;
    }
}