package com.ticketbot;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

public class Main {
    public static void main(String[] args) throws Exception {
        String token = System.getenv("BOT_TOKEN"); // ضع التوكن هنا

        JDABuilder builder = JDABuilder.createDefault(token);

        // تفعيل النوايا وخيارات الصوت
        builder.enableIntents(
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.MESSAGE_CONTENT,
                GatewayIntent.GUILD_MEMBERS,
                GatewayIntent.GUILD_VOICE_STATES
        );

        // تفعيل كاش الصوت مهم جداً
        builder.enableCache(CacheFlag.VOICE_STATE);
        builder.setMemberCachePolicy(MemberCachePolicy.ALL);
        builder.setChunkingFilter(ChunkingFilter.ALL);

        // إضافة الـ Listener
        builder.addEventListeners(new TicketSystem());

        builder.build();
    }
}