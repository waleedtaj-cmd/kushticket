package com.ticketbot;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;

import java.awt.Color;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

public class TicketSystem extends ListenerAdapter {

    // ==========================================
    // إعدادات رتب الأقسام (يمكنك إضافة أي عدد من الـ IDs هنا)
    // ==========================================

    // 1. رتب الإدارة العليا (للتذاكر الإدارية الخاصة)
    private static final List<Long> HIGH_MANAGEMENT_ROLES = Arrays.asList(
            1534802512765255690L, // Administrator
            1542590923668725880L, // Senior Moderator
            1542591179353497691L  // Server Management
    );

    // 2. رتب المشرفين والدعم (للتذاكر العامة ودعم الأعضاء)
    private static final List<Long> MOD_AND_SUPPORT_ROLES = Arrays.asList(
            1534802509854674965L, // Moderator
            1537487589030760559L, // lolo
            1534802503353241622L  // Supporter
    );

    // ==========================================
    // استقبال التفاعلات والأزرار
    // ==========================================
    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (event.getGuild() == null || event.getMember() == null) return;

        String buttonId = event.getComponentId();

        // زر تكت الدعم الإداري
        if (buttonId.equals("create_admin_ticket")) {
            createTicketChannel(event, HIGH_MANAGEMENT_ROLES, "دعم-إداري");
        } 
        // زر تكت الدعم العام
        else if (buttonId.equals("create_general_ticket")) {
            createTicketChannel(event, MOD_AND_SUPPORT_ROLES, "دعم-عام");
        }
    }

    // ==========================================
    // دالة إنشاء قناة التكت والصلاحيات والتاقات
    // ==========================================
    private void createTicketChannel(ButtonInteractionEvent event, List<Long> roleIdsToPing, String ticketType) {
        Guild guild = event.getGuild();
        Member member = event.getMember();

        String channelName = ticketType + "-" + member.getUser().getName().toLowerCase();

        // إعداد الصلاحيات الأساسية: حظر الجميع وإعطاء صاحب التكت الصلاحية
        ChannelAction<TextChannel> channelAction = guild.createTextChannel(channelName)
                .addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
                .addPermissionOverride(member, EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY), null);

        // إضافة جميع الرتب الموجودة في القائمة وتصريح رؤيتها للقناة تلقائياً
        for (long roleId : roleIdsToPing) {
            Role role = guild.getRoleById(roleId);
            if (role != null) {
                channelAction.addPermissionOverride(role, EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY), null);
            }
        }

        // تنفيذ عملية إنشاء القناة فعلياً
        channelAction.queue(channel -> {
            // رد خاص للمستخدم أن التكت اتفتح
            event.reply("✅ تم إنشاء تكت " + ticketType + " بنجاح: " + channel.getAsMention()).setEphemeral(true).queue();

            // تجميع تاقات كل الرتب المطلوبة
            StringBuilder roleMentions = new StringBuilder();
            for (long roleId : roleIdsToPing) {
                roleMentions.append("<@&").append(roleId).append("> ");
            }

            // بناء رسالة الإمبد الترحيبية داخل التكت
            EmbedBuilder embed = new EmbedBuilder();
            embed.setColor(ticketType.equals("دعم-إداري") ? Color.RED : Color.BLUE);
            embed.setTitle("🎫 تكت جديد: " + ticketType);
            embed.setDescription("مرحباً بك " + member.getAsMention() + "!\nتم فتح التكت بنجاح، يرجى توضيح مشكلتك بالتفصيل وسيتم الرد عليك قريباً.");
            embed.addField("الطاقم المختص", roleMentions.toString(), false);
            embed.setTimestamp(java.time.Instant.now());

            // إرسال التاق النصي مع الإمبد في القناة الجديدة
            channel.sendMessage(roleMentions.toString()).setEmbeds(embed.build()).queue();
        });
    }
}
