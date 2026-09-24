package com.ticketbot;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

public class Main {
    public static void main(String[] args) throws Exception {
        String token = System.getenv("BOT_TOKEN");

        JDABuilder builder = JDABuilder.createDefault(token);

        // تفعيل النوايا الأساسية لإدارة التكتات والأعضاء
        builder.enableIntents(
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.MESSAGE_CONTENT,
                GatewayIntent.GUILD_MEMBERS
        );

        builder.setMemberCachePolicy(MemberCachePolicy.ALL);
        builder.setChunkingFilter(ChunkingFilter.ALL);

        // إضافة الـ Listener الخاص بنظام التكتات
        builder.addEventListeners(new TicketSystem());

        builder.build();
    }
}
