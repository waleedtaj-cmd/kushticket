package com.ticketbot.transcript;

import com.ticketbot.transcript.TranscriptModels.AttachmentEntry;
import com.ticketbot.transcript.TranscriptModels.EmbedEntry;
import com.ticketbot.transcript.TranscriptModels.TranscriptDocument;
import com.ticketbot.transcript.TranscriptModels.TranscriptMessage;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * يكتب الـ Transcript بثلاث صيغ:
 *   transcript.txt   -> سهل القراءة في أي برنامج
 *   transcript.html  -> تصميم جميل يشبه Discord (افتحه بالمتصفح)
 *   complaint.json   -> كل البيانات بشكل منظم (للبرمجة/الأرشفة)
 */
public final class TranscriptWriter {

    private static final String LINE = "=".repeat(48);
    private static final String THIN = "-".repeat(48);
    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"')\\]]+");

    private final DateTimeFormatter full;
    private final DateTimeFormatter time;
    private final ZoneId zone;

    public TranscriptWriter(ZoneId zone) {
        this.zone = zone;
        this.full = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(zone);
        this.time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(zone);
    }

    public void writeAll(TranscriptDocument doc, Path dir) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("transcript.txt"), toText(doc), StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("transcript.html"), toHtml(doc), StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("complaint.json"), toJson(doc).toPrettyString(), StandardCharsets.UTF_8);
    }

    // =====================================================================
    // TXT
    // =====================================================================

    public String toText(TranscriptDocument d) {
        StringBuilder sb = new StringBuilder();
        sb.append(LINE).append('\n')
          .append("KushTicket Complaint Transcript\n")
          .append(LINE).append("\n\n");

        sb.append("Ticket: ").append(d.ticketName()).append('\n')
          .append("Version: v").append("%03d".formatted(d.version())).append('\n')
          .append("Server: ").append(d.guildName()).append(" (").append(d.guildId()).append(")\n")
          .append("Channel: #").append(d.channelName()).append(" (").append(d.channelId()).append(")\n")
          .append("User: @").append(d.ownerName()).append(" (ID: ").append(d.ownerId()).append(")\n")
          .append("Complaint Type: ").append(d.typeEnglish()).append(" / ").append(d.typeArabic()).append('\n');
        if (!d.target().isBlank()) sb.append("Target: ").append(d.target()).append('\n');
        sb.append("Claimed By: ").append(d.claimedById() == null ? "None" :
                "@" + d.claimedByName() + " (ID: " + d.claimedById() + ")").append('\n')
          .append("Status: ").append(d.status()).append('\n');
        if (d.closedById() != null) {
            sb.append("Solved By: @").append(d.closedByName()).append(" (ID: ").append(d.closedById()).append(")\n");
        }
        sb.append("Created At: ").append(fmt(d.createdAt())).append('\n')
          .append("Saved At: ").append(fmt(d.savedAt())).append('\n')
          .append("Saved By: @").append(d.savedByName()).append(" (ID: ").append(d.savedById()).append(")\n")
          .append("Messages: ").append(d.messages().size()).append('\n')
          .append("Attachments: ").append(d.attachmentCount()).append("\n\n");

        sb.append(THIN).append("\nREASON\n").append(THIN).append('\n')
          .append(d.reason().isBlank() ? "-" : d.reason()).append("\n\n");
        sb.append(THIN).append("\nEVIDENCE (from form)\n").append(THIN).append('\n')
          .append(d.evidence().isBlank() ? "-" : d.evidence()).append("\n\n");

        sb.append(THIN).append("\nCHAT\n").append(THIN).append("\n\n");

        for (TranscriptMessage m : d.messages()) {
            sb.append('[').append(fmt(m.createdAt())).append("] @").append(m.authorUsername());
            if (!m.authorDisplayName().equals(m.authorUsername())) {
                sb.append(" (").append(m.authorDisplayName()).append(')');
            }
            sb.append(" [ID: ").append(m.authorId()).append(']');
            if (m.authorIsBot()) sb.append(" [BOT]");
            sb.append(":\n");

            if (m.systemType() != null) sb.append("  <System: ").append(m.systemType()).append(">\n");
            if (m.replyToId() != null) sb.append("  ↪ Reply to message ").append(m.replyToId()).append('\n');
            if (!m.content().isBlank()) sb.append(m.content()).append('\n');
            if (m.editedAt() != null) sb.append("  (edited ").append(fmt(m.editedAt())).append(")\n");

            for (EmbedEntry e : m.embeds()) {
                sb.append("  [Embed]");
                if (e.title() != null) sb.append(' ').append(e.title());
                sb.append('\n');
                if (e.description() != null) sb.append("    ").append(e.description().replace("\n", "\n    ")).append('\n');
                for (String f : e.fields()) sb.append("    • ").append(f.replace("\n", " ")).append('\n');
            }
            for (String s : m.stickers()) sb.append("  [Sticker] ").append(s).append('\n');
            for (AttachmentEntry a : m.attachments()) {
                sb.append("Attachment: ").append(a.fileName()).append(" (").append(humanSize(a.size())).append(")\n")
                  .append("  ").append(a.url()).append('\n');
                if (a.localFile() != null) sb.append("  Saved copy: ").append(a.localFile()).append('\n');
                if (a.note() != null) sb.append("  Note: ").append(a.note()).append('\n');
            }
            sb.append('\n');
        }

        var links = d.allLinks();
        if (!links.isEmpty()) {
            sb.append(THIN).append("\nEVIDENCE LINKS\n").append(THIN).append('\n');
            links.forEach(l -> sb.append("- ").append(l).append('\n'));
            sb.append('\n');
        }

        sb.append(LINE).append("\nEND OF TRANSCRIPT\n").append(LINE).append('\n');
        return sb.toString();
    }

    // =====================================================================
    // HTML
    // =====================================================================

    public String toHtml(TranscriptDocument d) {
        StringBuilder sb = new StringBuilder(64 * 1024);
        sb.append("""
                <!DOCTYPE html>
                <html lang="ar" dir="rtl">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>""").append(esc(d.ticketName())).append(" — KushTicket Transcript</title>\n")
          .append("""
                <style>
                  :root{--bg:#1e1f22;--panel:#2b2d31;--msg:#313338;--muted:#949ba4;--text:#dbdee1;--accent:#5865f2;--green:#23a55a;--red:#f23f43;}
                  *{box-sizing:border-box}
                  body{margin:0;background:var(--bg);color:var(--text);font-family:"Segoe UI",Tahoma,Arial,sans-serif;line-height:1.55}
                  header{background:linear-gradient(135deg,#5865f2,#3b44c4);padding:28px 24px;color:#fff}
                  header h1{margin:0 0 4px;font-size:24px}
                  header p{margin:0;opacity:.85}
                  .wrap{max-width:980px;margin:0 auto;padding:20px}
                  .meta{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:10px;margin-bottom:18px}
                  .card{background:var(--panel);border-radius:10px;padding:12px 14px}
                  .card b{display:block;color:var(--muted);font-size:12px;font-weight:600;margin-bottom:2px}
                  .block{background:var(--panel);border-radius:10px;padding:14px;margin-bottom:12px;white-space:pre-wrap}
                  .block h3{margin:0 0 6px;font-size:14px;color:var(--muted)}
                  .chat{background:var(--msg);border-radius:10px;padding:8px 0;margin-top:18px}
                  .m{display:flex;gap:12px;padding:8px 16px}
                  .m:hover{background:#2e3035}
                  .av{width:40px;height:40px;border-radius:50%;flex:none;background:#444}
                  .body{min-width:0;flex:1}
                  .head{display:flex;flex-wrap:wrap;gap:8px;align-items:baseline}
                  .name{font-weight:700;color:#fff}
                  .bot{background:var(--accent);color:#fff;font-size:10px;padding:1px 5px;border-radius:4px}
                  .id,.time{color:var(--muted);font-size:12px}
                  .content{white-space:pre-wrap;word-wrap:break-word;margin-top:2px}
                  .sys{color:var(--muted);font-style:italic}
                  .reply{color:var(--muted);font-size:12px}
                  .embed{border-inline-start:4px solid var(--accent);background:var(--panel);border-radius:6px;padding:8px 12px;margin-top:6px;max-width:560px}
                  .embed .t{font-weight:700}
                  .embed .f{font-size:13px;white-space:pre-wrap}
                  .att{margin-top:6px}
                  .att img,.att video{max-width:420px;max-height:340px;border-radius:8px;display:block}
                  .file{display:inline-block;background:var(--panel);border:1px solid #3f4147;border-radius:8px;padding:8px 12px;color:#00a8fc;text-decoration:none}
                  .note{color:var(--red);font-size:12px}
                  a{color:#00a8fc}
                  footer{text-align:center;color:var(--muted);padding:24px;font-size:12px}
                </style>
                </head>
                <body>
                """);

        sb.append("<header><div class=\"wrap\" style=\"padding:0\"><h1>🎫 KushTicket — ")
          .append(esc(d.ticketName())).append("</h1><p>Complaint Transcript · النسخة v")
          .append("%03d".formatted(d.version())).append("</p></div></header>\n<div class=\"wrap\">\n");

        sb.append("<div class=\"meta\">");
        card(sb, "صاحب الشكوى / User", "@" + d.ownerName() + " · " + d.ownerId());
        card(sb, "نوع الشكوى / Type", d.typeArabic() + " · " + d.typeEnglish());
        if (!d.target().isBlank()) card(sb, "المشكو عليه / Target", d.target());
        card(sb, "Claimed By", d.claimedById() == null ? "لا يوجد" : "@" + d.claimedByName() + " · " + d.claimedById());
        card(sb, "الحالة / Status", d.status());
        if (d.closedById() != null) card(sb, "تم الحل بواسطة", "@" + d.closedByName() + " · " + d.closedById());
        card(sb, "تاريخ الإنشاء / Created", fmt(d.createdAt()));
        card(sb, "تاريخ الحفظ / Saved", fmt(d.savedAt()));
        card(sb, "حفظ بواسطة / Saved by", "@" + d.savedByName() + " · " + d.savedById());
        card(sb, "الرسائل / المرفقات", d.messages().size() + " / " + d.attachmentCount());
        card(sb, "القناة / Channel", "#" + d.channelName() + " · " + d.channelId());
        card(sb, "السيرفر / Server", d.guildName());
        sb.append("</div>\n");

        sb.append("<div class=\"block\" dir=\"auto\"><h3>سبب الشكوى / Reason</h3>")
          .append(linkify(esc(d.reason().isBlank() ? "—" : d.reason()))).append("</div>\n");
        sb.append("<div class=\"block\" dir=\"auto\"><h3>الأدلة / Evidence</h3>")
          .append(linkify(esc(d.evidence().isBlank() ? "—" : d.evidence()))).append("</div>\n");

        sb.append("<div class=\"chat\" dir=\"ltr\">\n");
        for (TranscriptMessage m : d.messages()) {
            sb.append("<div class=\"m\" id=\"m").append(m.id()).append("\">")
              .append("<img class=\"av\" loading=\"lazy\" src=\"").append(esc(m.authorAvatarUrl())).append("\" alt=\"\">")
              .append("<div class=\"body\"><div class=\"head\"><span class=\"name\">")
              .append(esc(m.authorDisplayName())).append("</span>");
            if (m.authorIsBot()) sb.append("<span class=\"bot\">BOT</span>");
            sb.append("<span class=\"id\">@").append(esc(m.authorUsername())).append(" · ").append(m.authorId())
              .append("</span><span class=\"time\">").append(fmt(m.createdAt())).append("</span>");
            if (m.editedAt() != null) sb.append("<span class=\"time\">(edited)</span>");
            sb.append("</div>");

            if (m.replyToId() != null) {
                sb.append("<div class=\"reply\">↪ <a href=\"#m").append(m.replyToId()).append("\">رد على رسالة</a></div>");
            }
            if (m.systemType() != null) {
                sb.append("<div class=\"sys\">System: ").append(esc(m.systemType())).append("</div>");
            }
            if (!m.content().isBlank()) {
                sb.append("<div class=\"content\" dir=\"auto\">").append(linkify(esc(m.content()))).append("</div>");
            }
            for (EmbedEntry e : m.embeds()) {
                sb.append("<div class=\"embed\" dir=\"auto\">");
                if (e.title() != null) sb.append("<div class=\"t\">").append(esc(e.title())).append("</div>");
                if (e.description() != null) sb.append("<div class=\"f\">").append(linkify(esc(e.description()))).append("</div>");
                for (String f : e.fields()) sb.append("<div class=\"f\">• ").append(linkify(esc(f))).append("</div>");
                if (e.imageUrl() != null) sb.append("<div class=\"att\"><img loading=\"lazy\" src=\"").append(esc(e.imageUrl())).append("\"></div>");
                if (e.footer() != null) sb.append("<div class=\"id\">").append(esc(e.footer())).append("</div>");
                sb.append("</div>");
            }
            for (String s : m.stickers()) sb.append("<div class=\"sys\">[Sticker] ").append(esc(s)).append("</div>");
            for (AttachmentEntry a : m.attachments()) {
                sb.append("<div class=\"att\">");
                // نستخدم النسخة المحلية أولاً، وإذا لم تتوفر نرجع لرابط Discord
                String src = a.localFile() != null ? a.localFile() : a.url();
                String fallback = "this.onerror=null;this.src='" + esc(a.url()) + "'";
                if (a.image()) {
                    sb.append("<a href=\"").append(esc(src)).append("\" target=\"_blank\"><img loading=\"lazy\" src=\"")
                      .append(esc(src)).append("\" onerror=\"").append(fallback).append("\" alt=\"")
                      .append(esc(a.fileName())).append("\"></a>");
                } else if (a.video()) {
                    sb.append("<video controls preload=\"metadata\" src=\"").append(esc(src)).append("\"></video>");
                } else if (a.contentType() != null && a.contentType().startsWith("audio")) {
                    sb.append("<audio controls preload=\"metadata\" src=\"").append(esc(src)).append("\"></audio><br>");
                }
                sb.append("<a class=\"file\" href=\"").append(esc(src)).append("\" target=\"_blank\">📎 ")
                  .append(esc(a.fileName())).append(" (").append(humanSize(a.size())).append(")</a> ")
                  .append("<a class=\"id\" href=\"").append(esc(a.url())).append("\" target=\"_blank\">Discord link</a>");
                if (a.note() != null) sb.append("<div class=\"note\">").append(esc(a.note())).append("</div>");
                sb.append("</div>");
            }
            sb.append("</div></div>\n");
        }
        sb.append("</div>\n");

        var links = d.allLinks();
        if (!links.isEmpty()) {
            sb.append("<div class=\"block\" style=\"margin-top:18px\"><h3>روابط الأدلة / Evidence Links</h3>");
            for (String l : links) sb.append("<div dir=\"ltr\"><a href=\"").append(esc(l)).append("\" target=\"_blank\">")
                    .append(esc(l)).append("</a></div>");
            sb.append("</div>");
        }

        sb.append("</div><footer>END OF TRANSCRIPT · Generated by KushTicket · ")
          .append(fmt(d.savedAt())).append(" (").append(esc(zone.getId())).append(")</footer>\n</body>\n</html>\n");
        return sb.toString();
    }

    // =====================================================================
    // JSON
    // =====================================================================

    public DataObject toJson(TranscriptDocument d) {
        DataArray messages = DataArray.empty();
        for (TranscriptMessage m : d.messages()) {
            DataArray atts = DataArray.empty();
            for (AttachmentEntry a : m.attachments()) {
                atts.add(DataObject.empty()
                        .put("fileName", a.fileName())
                        .put("url", a.url())
                        .put("proxyUrl", a.proxyUrl())
                        .put("size", a.size())
                        .put("contentType", a.contentType())
                        .put("image", a.image())
                        .put("video", a.video())
                        .put("localFile", a.localFile())
                        .put("note", a.note()));
            }
            DataArray embeds = DataArray.empty();
            for (EmbedEntry e : m.embeds()) {
                embeds.add(DataObject.empty()
                        .put("title", e.title())
                        .put("description", e.description())
                        .put("url", e.url())
                        .put("imageUrl", e.imageUrl())
                        .put("fields", DataArray.fromCollection(e.fields()))
                        .put("footer", e.footer()));
            }
            messages.add(DataObject.empty()
                    .put("id", m.id())
                    .put("authorId", m.authorId())
                    .put("authorUsername", m.authorUsername())
                    .put("authorDisplayName", m.authorDisplayName())
                    .put("authorIsBot", m.authorIsBot())
                    .put("createdAt", m.createdAt().toString())
                    .put("editedAt", m.editedAt() == null ? null : m.editedAt().toString())
                    .put("content", m.content())
                    .put("rawContent", m.rawContent())
                    .put("replyToId", m.replyToId())
                    .put("systemType", m.systemType())
                    .put("attachments", atts)
                    .put("embeds", embeds)
                    .put("stickers", DataArray.fromCollection(m.stickers()))
                    .put("links", DataArray.fromCollection(m.links())));
        }

        return DataObject.empty()
                .put("format", "kushticket-transcript/1")
                .put("ticket", DataObject.empty()
                        .put("name", d.ticketName())
                        .put("number", d.ticketNumber())
                        .put("version", d.version())
                        .put("guildId", d.guildId())
                        .put("guildName", d.guildName())
                        .put("channelId", d.channelId())
                        .put("channelName", d.channelName())
                        .put("ownerId", d.ownerId())
                        .put("ownerName", d.ownerName())
                        .put("type", d.typeEnglish())
                        .put("typeArabic", d.typeArabic())
                        .put("target", d.target())
                        .put("reason", d.reason())
                        .put("evidence", d.evidence())
                        .put("claimedById", d.claimedById())
                        .put("claimedByName", d.claimedByName())
                        .put("status", d.status())
                        .put("closedById", d.closedById())
                        .put("closedByName", d.closedByName())
                        .put("createdAt", d.createdAt() == null ? null : d.createdAt().toString())
                        .put("savedAt", d.savedAt().toString())
                        .put("savedById", d.savedById())
                        .put("savedByName", d.savedByName())
                        .put("messageCount", d.messages().size())
                        .put("attachmentCount", d.attachmentCount())
                        .put("evidenceLinks", DataArray.fromCollection(d.allLinks())))
                .put("messages", messages);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private void card(StringBuilder sb, String label, String value) {
        sb.append("<div class=\"card\"><b>").append(esc(label)).append("</b><span dir=\"auto\">")
          .append(esc(value)).append("</span></div>");
    }

    private String fmt(Instant i) {
        return i == null ? "-" : full.format(i);
    }

    private String fmt(OffsetDateTime t) {
        return t == null ? "-" : time.format(t);
    }

    static String esc(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '&' -> out.append("&amp;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /** يحول الروابط (بعد escape) إلى روابط قابلة للضغط. */
    private static String linkify(String escaped) {
        Matcher m = URL.matcher(escaped);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String url = m.group();
            m.appendReplacement(sb, Matcher.quoteReplacement(
                    "<a href=\"" + url + "\" target=\"_blank\" rel=\"noopener\">" + url + "</a>"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return "%.1f KB".formatted(bytes / 1024.0);
        return "%.1f MB".formatted(bytes / (1024.0 * 1024.0));
    }
}
