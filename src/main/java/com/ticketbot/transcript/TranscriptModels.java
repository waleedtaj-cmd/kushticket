package com.ticketbot.transcript;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * نماذج البيانات (records) الخاصة بالـ Transcript.
 *
 * record في Java 21 = كلاس بيانات غير قابل للتعديل. مثال:
 *   AttachmentEntry a = new AttachmentEntry("img.png", ...);
 *   a.fileName()  // يرجع "img.png"
 */
public final class TranscriptModels {

    private TranscriptModels() {}

    /** مرفق (صورة / فيديو / ملف / تسجيل صوتي). */
    public record AttachmentEntry(
            String fileName,
            String url,
            String proxyUrl,
            long size,
            String contentType,
            boolean image,
            boolean video,
            String localFile,      // المسار داخل مجلد الـ Transcript (attachments/...) أو null
            String note            // سبب عدم التحميل إن وجد
    ) {}

    /** Embed داخل رسالة (مثل رسالة التحكم الخاصة بالبوت). */
    public record EmbedEntry(
            String title,
            String description,
            String url,
            String imageUrl,
            List<String> fields,
            String footer
    ) {}

    /** رسالة واحدة داخل التذكرة. */
    public record TranscriptMessage(
            String id,
            String authorId,
            String authorUsername,
            String authorDisplayName,
            String authorAvatarUrl,
            boolean authorIsBot,
            OffsetDateTime createdAt,
            OffsetDateTime editedAt,
            String content,        // نص مقروء (المنشن يظهر كـ @name)
            String rawContent,     // النص الخام كما خزنه Discord (<@123>)
            String replyToId,
            String systemType,     // null للرسائل العادية، أو مثل CHANNEL_PINNED_ADD
            List<AttachmentEntry> attachments,
            List<EmbedEntry> embeds,
            List<String> stickers,
            List<String> links
    ) {}

    /** معلومات الشكوى + كل الرسائل. هذا ما يتم تحويله لـ TXT / HTML / JSON. */
    public record TranscriptDocument(
            String ticketName,
            int ticketNumber,
            int version,
            String guildName,
            String guildId,
            String channelName,
            String channelId,
            String ownerId,
            String ownerName,
            String typeArabic,
            String typeEnglish,
            String target,
            String reason,
            String evidence,
            String claimedById,      // null إن لم يوجد
            String claimedByName,
            String status,
            String closedById,
            String closedByName,
            Instant createdAt,
            Instant savedAt,
            String savedById,
            String savedByName,
            List<TranscriptMessage> messages
    ) {
        public int attachmentCount() {
            return messages.stream().mapToInt(m -> m.attachments().size()).sum();
        }

        public List<String> allLinks() {
            return messages.stream().flatMap(m -> m.links().stream()).distinct().toList();
        }
    }
}
