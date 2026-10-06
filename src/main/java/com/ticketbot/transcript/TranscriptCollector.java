package com.ticketbot.transcript;

import com.ticketbot.transcript.TranscriptModels.AttachmentEntry;
import com.ticketbot.transcript.TranscriptModels.EmbedEntry;
import com.ticketbot.transcript.TranscriptModels.TranscriptMessage;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.sticker.StickerItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * يجلب كل رسائل قناة التذكرة ويحولها إلى TranscriptMessage.
 *
 * ===== كيف نجلب كل الرسائل (Pagination)؟ =====
 * Discord API يرجع 100 رسالة كحد أقصى في كل طلب.
 * channel.getIterableHistory() يرجع MessagePaginationAction:
 *   - الطلب 1: أحدث 100 رسالة.
 *   - الطلب 2: 100 رسالة قبل (before) أقدم رسالة من الطلب 1.
 *   - ... وهكذا حتى يرجع Discord قائمة فارغة = وصلنا لأول رسالة في القناة.
 * forEachAsync يقوم بكل هذا تلقائيًا وبدون تجميد البوت (Async)،
 * ويحترم Rate Limits الخاصة بـ Discord.
 *
 * الرسائل تصل من الأحدث للأقدم، لذلك نعكسها في النهاية (reversed) لتصبح من الأقدم للأحدث.
 */
public final class TranscriptCollector {

    private static final Logger LOG = LoggerFactory.getLogger(TranscriptCollector.class);
    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"')\\]]+");

    /** حد أمان: لا نجلب أكثر من 50 ألف رسالة لتذكرة واحدة. */
    private static final int MAX_MESSAGES = 50_000;

    private TranscriptCollector() {}

    /** يجلب كل الرسائل من أول رسالة حتى الآن، مرتبة من الأقدم للأحدث. */
    public static CompletableFuture<List<Message>> fetchAllMessages(MessageChannel channel) {
        List<Message> newestFirst = new ArrayList<>();
        return channel.getIterableHistory()
                .cache(false)              // لا نحتاج حفظها في كاش JDA
                .forEachAsync(message -> {
                    newestFirst.add(message);
                    return newestFirst.size() < MAX_MESSAGES; // false = توقف
                })
                .thenApply(ignored -> {
                    LOG.info("Fetched {} messages from #{}", newestFirst.size(), channel.getName());
                    return newestFirst.reversed(); // Java 21: عكس القائمة => الأقدم أولاً
                });
    }

    /**
     * يحمّل المرفقات إلى مجلد attachments داخل مجلد الـ Transcript.
     * مهم جدًا: روابط مرفقات Discord تنتهي صلاحيتها بعد فترة، وتُحذف إذا حُذفت القناة.
     * لذلك نحفظ نسخة محلية من الأدلة.
     */
    public static CompletableFuture<List<TranscriptMessage>> convertAndDownload(
            List<Message> messages, Path transcriptDir, long maxBytesPerFile) {

        Path attachmentsDir = transcriptDir.resolve("attachments");
        List<CompletableFuture<TranscriptMessage>> futures = new ArrayList<>(messages.size());

        for (Message msg : messages) {
            List<CompletableFuture<AttachmentEntry>> attFutures = new ArrayList<>();
            int index = 0;
            for (Message.Attachment att : msg.getAttachments()) {
                attFutures.add(downloadAttachment(msg, att, index++, attachmentsDir, maxBytesPerFile));
            }
            CompletableFuture<TranscriptMessage> f = CompletableFuture
                    .allOf(attFutures.toArray(CompletableFuture[]::new))
                    .thenApply(v -> toTranscriptMessage(msg, attFutures.stream().map(CompletableFuture::join).toList()));
            futures.add(f);
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(v -> futures.stream().map(CompletableFuture::join).toList());
    }

    private static CompletableFuture<AttachmentEntry> downloadAttachment(
            Message msg, Message.Attachment att, int index, Path dir, long maxBytes) {

        String safeName = msg.getId() + "-" + index + "-" + sanitize(att.getFileName());
        String relative = "attachments/" + safeName;
        boolean image = att.isImage();
        boolean video = att.isVideo();

        if (att.getSize() > maxBytes) {
            return CompletableFuture.completedFuture(entry(att, image, video, null,
                    "لم يتم التحميل: الحجم أكبر من الحد المسموح"));
        }
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            return CompletableFuture.completedFuture(entry(att, image, video, null, "فشل إنشاء المجلد"));
        }
        return att.getProxy().downloadToPath(dir.resolve(safeName))
                .thenApply(path -> entry(att, image, video, relative, null))
                .exceptionally(err -> {
                    LOG.warn("Failed to download attachment {}: {}", att.getUrl(), err.getMessage());
                    return entry(att, image, video, null, "فشل التحميل: " + err.getMessage());
                });
    }

    private static AttachmentEntry entry(Message.Attachment att, boolean image, boolean video,
                                         String local, String note) {
        return new AttachmentEntry(att.getFileName(), att.getUrl(), att.getProxyUrl(), att.getSize(),
                att.getContentType(), image, video, local, note);
    }

    private static TranscriptMessage toTranscriptMessage(Message msg, List<AttachmentEntry> attachments) {
        User author = msg.getAuthor();
        Member member = msg.getMember();
        String display = member != null ? member.getEffectiveName() : author.getEffectiveName();

        List<EmbedEntry> embeds = new ArrayList<>();
        for (MessageEmbed e : msg.getEmbeds()) {
            List<String> fields = e.getFields().stream()
                    .map(f -> f.getName() + ": " + f.getValue())
                    .toList();
            embeds.add(new EmbedEntry(
                    e.getTitle(),
                    e.getDescription(),
                    e.getUrl(),
                    e.getImage() != null ? e.getImage().getUrl() : null,
                    fields,
                    e.getFooter() != null ? e.getFooter().getText() : null));
        }

        // روابط الأدلة: من نص الرسالة + روابط الـ Embeds
        Set<String> links = new LinkedHashSet<>();
        Matcher m = URL.matcher(msg.getContentRaw());
        while (m.find()) links.add(m.group());
        for (EmbedEntry e : embeds) {
            if (e.url() != null) links.add(e.url());
        }

        List<String> stickers = msg.getStickers().stream().map(StickerItem::getName).toList();

        MessageType type = msg.getType();
        String systemType = (type == MessageType.DEFAULT || type == MessageType.INLINE_REPLY) ? null : type.name();

        String replyTo = msg.getMessageReference() != null ? msg.getMessageReference().getMessageId() : null;

        return new TranscriptMessage(
                msg.getId(),
                author.getId(),
                author.getName(),
                display,
                author.getEffectiveAvatarUrl(),
                author.isBot(),
                msg.getTimeCreated(),
                msg.getTimeEdited(),
                msg.getContentDisplay(),
                msg.getContentRaw(),
                replyTo,
                systemType,
                attachments,
                embeds,
                stickers,
                List.copyOf(links));
    }

    /** اسم ملف آمن (بدون / أو \ أو رموز غريبة). */
    public static String sanitize(String name) {
        String cleaned = name.replaceAll("[^\\p{L}\\p{N}._-]", "_");
        return cleaned.length() > 80 ? cleaned.substring(cleaned.length() - 80) : cleaned;
    }
}
