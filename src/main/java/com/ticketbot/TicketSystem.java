package com.ticketbot;

import java.awt.Color;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;

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
            handleVoiceRequest(event);
        } else if (componentId.startsWith("problem_solved:")) {
            handleProblemSolved(event);
        } else if (componentId.startsWith("save_ticket:")) {
            handleSaveTicketClick(event);
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
            Button voiceBtn = Button.primary("voice_request:" + member.getId(), "طلب توجه لروم الـ Help 🎧");
            Button solvedBtn = Button.success("problem_solved:" + member.getId(), "تم حل المشكلة ✅");
            Button saveBtn = Button.secondary("save_ticket:" + member.getId(), "حفظ الشكوى 💾");

            embed.setTitle(isStaffReport ? "⚠️ شكوى على إداري" : "📩 تذكرة الدعم الفني")
                 .setDescription("مرحباً بك " + member.getAsMention() + "!\n" +
                         (isStaffReport ? "إذا كانت الشكوى تتطلب نقاشاً صوتياً، يرجى التوجه إلى روم الـ Help الصوتي والضغط على الزر أدناه لإبلاغ الإدارة." 
                                       : "يرجى كتابة التفاصيل وسيتم الرد عليك قريباً."))
                 .setColor(isStaffReport ? Color.RED : Color.BLUE);

            channel.sendMessageEmbeds(embed.build()).setActionRow(voiceBtn, solvedBtn, saveBtn).queue();
        });
    }

    private void handleVoiceRequest(ButtonInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) return;

        Role voiceRole = guild.getRoleById(VOICE_ACCESS_ROLE_ID);
        if (voiceRole != null) {
            guild.addRoleToMember(member, voiceRole).queue();
        }

        VoiceChannel helpVoiceChannel = guild.getVoiceChannelById(HELP_VOICE_CHANNEL_ID);
        String channelMention = (helpVoiceChannel != null) ? helpVoiceChannel.getAsMention() : "روم الـ Help";

        event.reply("🎧 قام العضو طلب التوجه للصوت. يرجى من الإدارة التوجه إلى " + channelMention + " لمتابعة الشكوى.").queue();
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
            target.ban(0, TimeUnit.DAYS).reason("بناءً على الشكوى الإدارية").queue(
                s -> event.reply("✅ تم حظر العضو بنجاح " + target.getAsMention()).setEphemeral(true).queue(),
                e -> event.reply("❌ فشل حظر العضو.").setEphemeral(true).queue()
            );
        });
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
