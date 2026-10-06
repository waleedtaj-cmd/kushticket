package com.ticketbot.ticket;

/**
 * كل Custom IDs الخاصة بالأزرار والـ Modals في مكان واحد.
 *
 * لماذا لا نضع User ID أو Ticket ID داخل أزرار التحكم؟
 * لأن الأزرار موجودة داخل قناة التذكرة نفسها، فنعرف التذكرة من event.getChannel().
 * والبيانات الحقيقية (من صاحب التذكرة؟ من عمل Claim؟) محفوظة في السيرفر (TicketStore)،
 * وليس في الزر. المستخدم لا يستطيع التلاعب بها.
 */
public final class TicketIds {

    private TicketIds() {}

    // ===== أزرار رسالة التحكم الرئيسية =====
    public static final String CLAIM = "ticket_claim";
    public static final String UNCLAIM = "ticket_unclaim";
    public static final String SAVE = "ticket_save";
    public static final String CLOSE = "ticket_close";

    // ===== تأكيد الإغلاق (رسالة Ephemeral تظهر للإداري فقط) =====
    public static final String CLOSE_CONFIRM = "ticket_close_confirm";
    public static final String CLOSE_CANCEL = "ticket_close_cancel";

    // ===== التسجيل الصوتي =====
    public static final String VOICE_START = "voice_start";
    public static final String VOICE_STOP = "voice_stop";

    // ===== فتح تذكرة: ticket_open:ADMIN_COMPLAINT =====
    public static final String OPEN_PREFIX = "ticket_open:";
    // ===== Modal: ticket_modal:ADMIN_COMPLAINT =====
    public static final String MODAL_PREFIX = "ticket_modal:";

    // ===== حقول الـ Modal =====
    public static final String FIELD_TARGET = "target";
    public static final String FIELD_REASON = "reason";
    public static final String FIELD_EVIDENCE = "evidence";

    // ===== Slash command =====
    public static final String SETUP_COMMAND = "ticket-setup";
}
