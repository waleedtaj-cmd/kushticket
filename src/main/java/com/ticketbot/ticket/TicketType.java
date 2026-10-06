package com.ticketbot.ticket;

/**
 * أنواع التذاكر.
 * enum = مجموعة ثابتة من القيم. كل قيمة لها نص عربي، نص إنجليزي، بادئة اسم القناة، وإيموجي.
 */
public enum TicketType {
    ADMIN_COMPLAINT("شكوى على إداري", "Complaint Against Admin", "complaint-admin", "🛡️", true, true),
    GENERAL_ISSUE("مشكلة عامة", "General Issue", "issue", "❓", false, false);

    private final String arabicLabel;
    private final String englishLabel;
    private final String channelPrefix;
    private final String emoji;
    private final boolean needsTarget;
    private final boolean voice;

    TicketType(String arabicLabel, String englishLabel, String channelPrefix,
               String emoji, boolean needsTarget, boolean voice) {
        this.arabicLabel = arabicLabel;
        this.englishLabel = englishLabel;
        this.channelPrefix = channelPrefix;
        this.emoji = emoji;
        this.needsTarget = needsTarget;
        this.voice = voice;
    }

    public String arabicLabel() { return arabicLabel; }
    public String englishLabel() { return englishLabel; }
    public String channelPrefix() { return channelPrefix; }
    public String emoji() { return emoji; }
    /** هل نطلب اسم/ID الشخص المشكو عليه؟ */
    public boolean needsTarget() { return needsTarget; }
    /** هل التذكرة تدعم التسجيل الصوتي؟ */
    public boolean isVoice() { return voice; }

    /** آمن: يرجع null بدل أن يرمي Exception لو كان النص غير صحيح (مثلاً Custom ID تم التلاعب به). */
    public static TicketType parse(String name) {
        for (TicketType t : values()) {
            if (t.name().equals(name)) {
                return t;
            }
        }
        return null;
    }
}
