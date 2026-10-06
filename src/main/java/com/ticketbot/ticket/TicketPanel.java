package com.ticketbot.ticket;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * يبني "رسالة التحكم الرئيسية" للتذكرة (Embed + أزرار) من بيانات Ticket.
 *
 * الفكرة المهمة: الرسالة دائمًا تُبنى من البيانات الحالية.
 * لذلك بعد أي تغيير (Claim / Unclaim / Save / Close / Recording)
 * نعيد بناء الـ Embed ونعمل Edit لنفس الرسالة. لا نرسل رسالة جديدة.
 */
public final class TicketPanel {

    private static final Logger LOG = LoggerFactory.getLogger(TicketPanel.class);

    private static final Color OPEN_COLOR = new Color(0x2ECC71);
    private static final Color CLAIMED_COLOR = new Color(0x3498DB);
    private static final Color SOLVED_COLOR = new Color(0x95A5A6);

    private TicketPanel() {}

    public static MessageEmbed embed(Ticket t) {
        boolean solved = t.status() == Ticket.Status.SOLVED;
        long claimer = t.claimedById();

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🎫 KushTicket — " + t.type().emoji() + " " + t.type().arabicLabel())
                .setColor(solved ? SOLVED_COLOR : (claimer != 0 ? CLAIMED_COLOR : OPEN_COLOR))
                .addField("نوع الشكوى", t.type().arabicLabel(), true)
                .addField("صاحب الشكوى", "<@" + t.ownerId() + ">", true)
                .addField("الحالة", statusText(t), true)
                .addField("Claimed by",
                        claimer == 0 ? "لا يوجد" : "<@" + claimer + "> " + ts(t.claimedAt(), "R"),
                        true)
                .addField("رقم التذكرة", "`" + t.displayName() + "`", true)
                .addField("تاريخ الإنشاء", ts(t.createdAt(), "f"), true);

        if (t.type().needsTarget() && !t.target().isBlank()) {
            eb.addField("المشكو عليه", cut(t.target(), 1024), false);
        }
        eb.addField("سبب الشكوى", t.reason().isBlank() ? "—" : cut(t.reason(), 1024), false);
        eb.addField("الأدلة", t.evidence().isBlank() ? "لا يوجد (يمكن إرسالها داخل التذكرة)" : cut(t.evidence(), 1024), false);

        if (t.type().isVoice()) {
            eb.addField("🎙️ التسجيل الصوتي", voiceText(t), false);
        }

        if (t.savedVersion() > 0) {
            eb.addField("💾 آخر حفظ",
                    "النسخة `v%03d` بواسطة <@%d> %s".formatted(t.savedVersion(), t.lastSavedById(), ts(t.lastSavedAt(), "R")),
                    false);
        }

        if (solved) {
            String closed = "تم الحل بواسطة <@" + t.closedById() + "> " + ts(t.closedAt(), "R");
            if (t.deleteAt() != null) {
                closed += "\nسيتم حذف القناة " + ts(t.deleteAt(), "R");
            }
            eb.addField("✅ الإغلاق", closed, false);
        }

        eb.setFooter("أزرار Claim / حفظ الشكوى / تم حل المشكلة للإداريين فقط");
        return eb.build();
    }

    public static List<ActionRow> rows(Ticket t) {
        boolean solved = t.status() == Ticket.Status.SOLVED;
        List<ActionRow> rows = new ArrayList<>();

        Button claimButton = t.claimedById() == 0
                ? Button.success(TicketIds.CLAIM, "Claim").withEmoji(Emoji.fromUnicode("✋"))
                : Button.secondary(TicketIds.UNCLAIM, "Unclaim").withEmoji(Emoji.fromUnicode("↩️"));

        rows.add(ActionRow.of(
                claimButton.withDisabled(solved),
                Button.primary(TicketIds.SAVE, "حفظ الشكوى").withEmoji(Emoji.fromUnicode("💾")).withDisabled(solved),
                Button.danger(TicketIds.CLOSE, "تم حل المشكلة").withEmoji(Emoji.fromUnicode("✅")).withDisabled(solved)
        ));

        if (t.type().isVoice()) {
            Ticket.VoiceStatus vs = t.voiceStatus();
            boolean idle = vs == Ticket.VoiceStatus.IDLE;
            boolean active = vs == Ticket.VoiceStatus.CONNECTING || vs == Ticket.VoiceStatus.RECORDING;
            rows.add(ActionRow.of(
                    Button.success(TicketIds.VOICE_START, "بدء التسجيل").withEmoji(Emoji.fromUnicode("🎙️"))
                            .withDisabled(solved || !idle),
                    Button.danger(TicketIds.VOICE_STOP, "إنهاء التسجيل").withEmoji(Emoji.fromUnicode("⏹️"))
                            .withDisabled(solved || !active)
            ));
        }
        return rows;
    }

    /**
     * تعديل رسالة التحكم عن طريق ID (نستخدمها عندما لا يكون عندنا Button Event،
     * مثلاً بعد انتهاء الحفظ أو عندما يتصل البوت بالـ Voice).
     */
    public static void refresh(Guild guild, Ticket t) {
        if (guild == null || t.controlMessageId() == 0) return;
        TextChannel channel = guild.getTextChannelById(t.channelId());
        if (channel == null) return;
        channel.editMessageEmbedsById(t.controlMessageId(), embed(t))
                .setComponents(rows(t))
                .queue(null, err -> LOG.warn("Could not refresh control message of {}: {}", t.displayName(), err.getMessage()));
    }

    private static String statusText(Ticket t) {
        if (t.status() == Ticket.Status.SOLVED) return "✅ تم الحل";
        return t.claimedById() == 0 ? "🟢 مفتوحة" : "🔵 مفتوحة (قيد المعالجة)";
    }

    private static String voiceText(Ticket t) {
        return switch (t.voiceStatus()) {
            case IDLE -> t.recordingsCount() == 0
                    ? "⚪ لم يبدأ — ادخل روم صوتي ثم اضغط «بدء التسجيل»"
                    : "⚪ متوقف — عدد التسجيلات المحفوظة: " + t.recordingsCount();
            case CONNECTING -> "⏳ جارٍ الاتصال بالروم الصوتي...";
            case RECORDING -> "🔴 جارٍ التسجيل " + ts(t.recordingStartedAt(), "R");
            case SAVING -> "💾 جارٍ حفظ التسجيل...";
        };
    }

    /** Discord timestamp: يظهر لكل شخص بتوقيته المحلي. */
    public static String ts(Instant instant, String style) {
        return instant == null ? "—" : "<t:" + instant.getEpochSecond() + ":" + style + ">";
    }

    public static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
