package com.ticketbot.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * إعدادات البوت.
 *
 * تُقرأ من ملف config.properties الموجود في مجلد المشروع (نفس مكان pom.xml).
 * أي قيمة يمكن أيضًا تمريرها كـ Environment Variable (مثلاً KUSHTICKET_TOKEN)
 * وهي تأخذ الأولوية على الملف.
 *
 * record = كلاس بيانات (Java 16+) يولّد تلقائيًا الـ constructor والـ getters.
 * مثلاً: config.token() و config.staffRoleIds()
 */
public record BotConfig(
        String token,
        long guildId,
        Set<Long> staffRoleIds,
        long ticketCategoryId,
        long transcriptChannelId,
        long voiceComplaintRoleId,
        Path dataDir,
        Path transcriptsDir,
        Path recordingsDir,
        ZoneId zone,
        int deleteChannelAfterCloseSeconds,
        int maxRecordingMinutes,
        long maxAttachmentDownloadBytes
) {

    public static BotConfig load() throws IOException {
        Properties props = new Properties();
        Path file = Path.of("config.properties");
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                props.load(reader);
            }
        } else {
            System.err.println("[KushTicket] config.properties not found at: " + file.toAbsolutePath());
        }

        String token = get(props, "token", "");
        if (token.isBlank()) {
            throw new IllegalStateException(
                    "Bot token is missing. Put token=... in config.properties or set KUSHTICKET_TOKEN.");
        }

        Set<Long> staffRoles = Arrays.stream(get(props, "staff.role.ids", "").split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toUnmodifiableSet());

        String zoneId = get(props, "timezone", "");
ZoneId zone;
try {
    zone = zoneId.isBlank() ? ZoneId.systemDefault() : ZoneId.of(zoneId);
} catch (Exception e) {
    System.err.println("[KushTicket] Invalid timezone '" + zoneId + "', falling back to system default.");
    zone = ZoneId.systemDefault();
}

        Path dataDir = Path.of(get(props, "data.dir", "data"));

        return new BotConfig(
                token,
                parseLong(get(props, "guild.id", "0")),
                staffRoles,
                parseLong(get(props, "ticket.category.id", "0")),
                parseLong(get(props, "transcript.channel.id", "0")),
                parseLong(get(props, "voice.complaint.role.id", "0")),
                dataDir,
                Path.of(get(props, "transcripts.dir", dataDir.resolve("transcripts").toString())),
                Path.of(get(props, "recordings.dir", dataDir.resolve("recordings").toString())),
                zone,
                Integer.parseInt(get(props, "close.delete.after.seconds", "10")),
                Integer.parseInt(get(props, "voice.max.minutes", "30")),
                Long.parseLong(get(props, "transcript.max.attachment.mb", "25")) * 1024L * 1024L
        );
    }

    /** يقرأ القيمة من Environment Variable أولاً، ثم من الملف. "staff.role.ids" -> KUSHTICKET_STAFF_ROLE_IDS */
    private static String get(Properties props, String key, String def) {
        String envKey = "KUSHTICKET_" + key.toUpperCase().replace('.', '_');
        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return props.getProperty(key, def).trim();
    }

    private static long parseLong(String value) {
        return value.isBlank() ? 0L : Long.parseLong(value);
    }
}
