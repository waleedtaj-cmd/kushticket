package com.ticketbot;

import com.ticketbot.complaint.ComplaintSaveSystem;
import com.ticketbot.config.BotConfig;
import com.ticketbot.ticket.ClaimSystem;
import com.ticketbot.ticket.TicketStore;
import com.ticketbot.ticket.TicketSystem;
import com.ticketbot.voice.VoiceComplaintSystem;
import moe.kyokobot.libdave.NativeDaveFactory;
import moe.kyokobot.libdave.jda.LDJDADaveSessionFactory;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.audio.AudioModuleConfig;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;

/**
 * نقطة تشغيل البوت.
 *
 * التغييرات المهمة مقارنة بالنسخة القديمة:
 *   1) setAudioModuleConfig(... DAVE ...) => بدونها Discord يرفض الاتصال الصوتي (Loop دخول/خروج).
 *   2) GatewayIntent.MESSAGE_CONTENT => بدونها محتوى الرسائل في الـ Transcript يكون فارغًا.
 *   3) GatewayIntent.GUILD_VOICE_STATES => لمعرفة الروم الصوتي الذي فيه العضو.
 *   4) قفل "نسخة واحدة فقط" => يمنع تشغيل البوت مرتين من Eclipse (سبب شائع لـ Loop الصوت).
 *   5) Shutdown hook => ينهي التسجيلات ويحفظ البيانات عند الإيقاف.
 */
public final class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    /** نحتفظ بالقفل طوال عمر البرنامج (لو أصبح garbage سيتحرر القفل). */
    private static FileLock instanceLock;

    public static void main(String[] args) throws Exception {
        BotConfig config = BotConfig.load();
        acquireSingleInstanceLock(config.dataDir());

        // تحميل مكتبة DAVE الأصلية (native). لو فشل => نعرف فورًا بدل Loop صامت.
        NativeDaveFactory.ensureAvailable();
        var daveSessionFactory = new LDJDADaveSessionFactory(new NativeDaveFactory());

        TicketStore store = TicketStore.load(config.dataDir());
        ComplaintSaveSystem saveSystem = new ComplaintSaveSystem(config, store);
        VoiceComplaintSystem voiceSystem = new VoiceComplaintSystem(config, store);
        ClaimSystem claimSystem = new ClaimSystem(config, store);
        TicketSystem ticketSystem = new TicketSystem(config, store, saveSystem, voiceSystem);

        JDA jda = JDABuilder.createDefault(config.token(), EnumSet.of(
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.MESSAGE_CONTENT,      // Privileged: فعّلها في Developer Portal
                        GatewayIntent.GUILD_VOICE_STATES))
                .setAudioModuleConfig(new AudioModuleConfig()
                        .withDaveSessionFactory(daveSessionFactory))
                // لا نحتاج هذه الكاشات، تعطيلها يزيل التحذيرات من الـ Console
                .disableCache(CacheFlag.EMOJI, CacheFlag.STICKER,
                        CacheFlag.SOUNDBOARD_SOUNDS, CacheFlag.SCHEDULED_EVENTS)
                .addEventListeners(ticketSystem, claimSystem, saveSystem, voiceSystem)
                .build();

        voiceSystem.setJda(jda);
        jda.awaitReady();

        // تسجيل الأوامر: في سيرفر محدد (فوري) أو Global (قد يأخذ وقت)
        Guild guild = config.guildId() == 0 ? null : jda.getGuildById(config.guildId());
        if (guild != null) {
            guild.updateCommands().addCommands(TicketSystem.setupCommand()).queue();
        } else {
            jda.updateCommands().addCommands(TicketSystem.setupCommand()).queue();
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down KushTicket...");
            voiceSystem.shutdown();   // ينهي التسجيلات ويغلق الاتصال الصوتي مرة واحدة
            store.save();
            saveSystem.shutdown();
            jda.shutdown();
        }, "kushticket-shutdown"));

        LOG.info("KushTicket is ready as {}", jda.getSelfUser().getName());
    }

    /**
     * يمنع تشغيل نسختين من البوت بنفس الوقت.
     * في Eclipse: الضغط على Run مرتين بدون Stop يشغل نسختين بنفس التوكن.
     * النسختان تتصارعان على الاتصال الصوتي => كل واحدة تطرد الأخرى => دخول/خروج متكرر،
     * ويستمر حتى بعد "إنهاء التسجيل" لأن النسخة الأخرى ما زالت تعمل.
     */
    private static void acquireSingleInstanceLock(Path dataDir) throws IOException {
        Files.createDirectories(dataDir);
        FileChannel channel = FileChannel.open(dataDir.resolve("kushticket.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        instanceLock = channel.tryLock();
        if (instanceLock == null) {
            // الرسالة بالإنجليزية لأن Console في Windows قد لا يعرض العربية بشكل صحيح
            System.err.println("""
                    ==========================================================
                    KushTicket is ALREADY RUNNING! Stop the old instance first.
                    Eclipse: open the Console view and press the red square
                    (Terminate) for every running KushTicket launch.
                    ==========================================================""");
            System.exit(1);
        }
    }
}
