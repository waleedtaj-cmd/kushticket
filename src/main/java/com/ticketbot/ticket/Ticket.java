package com.ticketbot.ticket;

import net.dv8tion.jda.api.utils.data.DataObject;

import java.time.Instant;

/**
 * بيانات تذكرة واحدة.
 *
 * كل الدوال التي تقرأ/تعدل البيانات عليها كلمة synchronized:
 * هذا يعني أن Thread واحد فقط يستطيع تنفيذها في نفس اللحظة لنفس التذكرة.
 * مثال: إداريان ضغطا Claim في نفس الملّي ثانية => واحد فقط سينجح، والثاني سيرى "مستلمة بالفعل".
 */
public final class Ticket {

    public enum Status { OPEN, SOLVED }

    public enum VoiceStatus { IDLE, CONNECTING, RECORDING, SAVING }

    public enum ClaimResult { CLAIMED, ALREADY_YOURS, CLAIMED_BY_OTHER, NOT_OPEN }

    public enum UnclaimResult { UNCLAIMED, NOT_CLAIMED, NOT_YOUR_CLAIM, NOT_OPEN }

    // ===== بيانات ثابتة (final) =====
    private final int number;
    private final long guildId;
    private final long channelId;
    private final long ownerId;
    private final TicketType type;
    private final String target;
    private final String reason;
    private final String evidence;
    private final Instant createdAt;

    // ===== بيانات تتغير =====
    private String channelName;
    private long controlMessageId;
    private Status status = Status.OPEN;
    private long claimedById;          // 0 = لا يوجد
    private Instant claimedAt;
    private long closedById;
    private Instant closedAt;
    private Instant deleteAt;

    // معلومات آخر حفظ (لمنع النسخ المكررة)
    private int savedVersion;          // 0 = لم يتم الحفظ بعد
    private long lastSavedMessageId;
    private Status lastSavedStatus;
    private long lastSavedClaimerId;
    private long lastSavedById;
    private Instant lastSavedAt;
    private String lastSavedPath;

    private int recordingsCount;

    // ===== بيانات وقت التشغيل فقط (لا تُحفظ في الملف) =====
    private VoiceStatus voiceStatus = VoiceStatus.IDLE;
    private Instant recordingStartedAt;

    public Ticket(int number, long guildId, long channelId, String channelName, long ownerId,
                  TicketType type, String target, String reason, String evidence, Instant createdAt) {
        this.number = number;
        this.guildId = guildId;
        this.channelId = channelId;
        this.channelName = channelName;
        this.ownerId = ownerId;
        this.type = type;
        this.target = target == null ? "" : target;
        this.reason = reason == null ? "" : reason;
        this.evidence = evidence == null ? "" : evidence;
        this.createdAt = createdAt;
    }

    // ================= Claim / Unclaim =================

    public synchronized ClaimResult tryClaim(long adminId) {
        if (status != Status.OPEN) return ClaimResult.NOT_OPEN;
        if (claimedById == adminId) return ClaimResult.ALREADY_YOURS;
        if (claimedById != 0) return ClaimResult.CLAIMED_BY_OTHER;
        claimedById = adminId;
        claimedAt = Instant.now();
        return ClaimResult.CLAIMED;
    }

    /** @param force true إذا كان المستخدم Administrator ويريد إلغاء Claim إداري آخر */
    public synchronized UnclaimResult tryUnclaim(long adminId, boolean force) {
        if (status != Status.OPEN) return UnclaimResult.NOT_OPEN;
        if (claimedById == 0) return UnclaimResult.NOT_CLAIMED;
        if (claimedById != adminId && !force) return UnclaimResult.NOT_YOUR_CLAIM;
        claimedById = 0;
        claimedAt = null;
        return UnclaimResult.UNCLAIMED;
    }

    // ================= Close =================

    /** يرجع true مرة واحدة فقط (أول إداري يؤكد الإغلاق). */
    public synchronized boolean tryMarkSolved(long adminId) {
        if (status != Status.OPEN) return false;
        status = Status.SOLVED;
        closedById = adminId;
        closedAt = Instant.now();
        return true;
    }

    /** في حال فشل الحفظ أثناء الإغلاق نرجع التذكرة مفتوحة حتى لا نحذف القناة بدون Transcript. */
    public synchronized void revertSolved() {
        status = Status.OPEN;
        closedById = 0;
        closedAt = null;
        deleteAt = null;
    }

    // ================= Save =================

    /** هل تغيّر شيء منذ آخر حفظ؟ (رسائل جديدة / حالة جديدة / Claim مختلف) */
    public synchronized boolean hasChangesSinceLastSave(long newestMessageId) {
        return savedVersion == 0
                || newestMessageId != lastSavedMessageId
                || status != lastSavedStatus
                || claimedById != lastSavedClaimerId;
    }

    public synchronized int nextSaveVersion() {
        return savedVersion + 1;
    }

    public synchronized void markSaved(int version, long newestMessageId, long savedBy, Instant at, String path) {
        this.savedVersion = version;
        this.lastSavedMessageId = newestMessageId;
        this.lastSavedStatus = status;
        this.lastSavedClaimerId = claimedById;
        this.lastSavedById = savedBy;
        this.lastSavedAt = at;
        this.lastSavedPath = path;
    }

    // ================= Getters / Setters =================

    public int number() { return number; }
    public long guildId() { return guildId; }
    public long channelId() { return channelId; }
    public long ownerId() { return ownerId; }
    public TicketType type() { return type; }
    public String target() { return target; }
    public String reason() { return reason; }
    public String evidence() { return evidence; }
    public Instant createdAt() { return createdAt; }

    public synchronized String channelName() { return channelName; }
    public synchronized void setChannelName(String name) { this.channelName = name; }
    public synchronized long controlMessageId() { return controlMessageId; }
    public synchronized void setControlMessageId(long id) { this.controlMessageId = id; }
    public synchronized Status status() { return status; }
    public synchronized long claimedById() { return claimedById; }
    public synchronized Instant claimedAt() { return claimedAt; }
    public synchronized long closedById() { return closedById; }
    public synchronized Instant closedAt() { return closedAt; }
    public synchronized Instant deleteAt() { return deleteAt; }
    public synchronized void setDeleteAt(Instant at) { this.deleteAt = at; }
    public synchronized int savedVersion() { return savedVersion; }
    public synchronized long lastSavedById() { return lastSavedById; }
    public synchronized Instant lastSavedAt() { return lastSavedAt; }
    public synchronized String lastSavedPath() { return lastSavedPath; }
    public synchronized int recordingsCount() { return recordingsCount; }
    public synchronized void incrementRecordings() { recordingsCount++; }
    public synchronized VoiceStatus voiceStatus() { return voiceStatus; }
    public synchronized Instant recordingStartedAt() { return recordingStartedAt; }

    public synchronized void setVoiceStatus(VoiceStatus status) {
        this.voiceStatus = status;
        this.recordingStartedAt = status == VoiceStatus.RECORDING ? Instant.now() : null;
    }

    public String displayName() {
        return "%s-%04d".formatted(type.channelPrefix(), number);
    }

    // ================= JSON =================

    public synchronized DataObject toData() {
        return DataObject.empty()
                .put("number", number)
                .put("guildId", guildId)
                .put("channelId", channelId)
                .put("channelName", channelName)
                .put("ownerId", ownerId)
                .put("type", type.name())
                .put("target", target)
                .put("reason", reason)
                .put("evidence", evidence)
                .put("createdAt", millis(createdAt))
                .put("controlMessageId", controlMessageId)
                .put("status", status.name())
                .put("claimedById", claimedById)
                .put("claimedAt", millis(claimedAt))
                .put("closedById", closedById)
                .put("closedAt", millis(closedAt))
                .put("deleteAt", millis(deleteAt))
                .put("savedVersion", savedVersion)
                .put("lastSavedMessageId", lastSavedMessageId)
                .put("lastSavedStatus", lastSavedStatus == null ? null : lastSavedStatus.name())
                .put("lastSavedClaimerId", lastSavedClaimerId)
                .put("lastSavedById", lastSavedById)
                .put("lastSavedAt", millis(lastSavedAt))
                .put("lastSavedPath", lastSavedPath)
                .put("recordingsCount", recordingsCount);
    }

    public static Ticket fromData(DataObject d) {
        TicketType type = TicketType.parse(d.getString("type", ""));
        if (type == null) type = TicketType.GENERAL_ISSUE;
        Ticket t = new Ticket(
                d.getInt("number", 0),
                d.getLong("guildId", 0),
                d.getLong("channelId", 0),
                d.getString("channelName", ""),
                d.getLong("ownerId", 0),
                type,
                d.getString("target", ""),
                d.getString("reason", ""),
                d.getString("evidence", ""),
                instant(d.getLong("createdAt", 0)));
        t.controlMessageId = d.getLong("controlMessageId", 0);
        t.status = Status.valueOf(d.getString("status", "OPEN"));
        t.claimedById = d.getLong("claimedById", 0);
        t.claimedAt = instant(d.getLong("claimedAt", 0));
        t.closedById = d.getLong("closedById", 0);
        t.closedAt = instant(d.getLong("closedAt", 0));
        t.deleteAt = instant(d.getLong("deleteAt", 0));
        t.savedVersion = d.getInt("savedVersion", 0);
        t.lastSavedMessageId = d.getLong("lastSavedMessageId", 0);
        String lss = d.getString("lastSavedStatus", null);
        t.lastSavedStatus = lss == null ? null : Status.valueOf(lss);
        t.lastSavedClaimerId = d.getLong("lastSavedClaimerId", 0);
        t.lastSavedById = d.getLong("lastSavedById", 0);
        t.lastSavedAt = instant(d.getLong("lastSavedAt", 0));
        t.lastSavedPath = d.getString("lastSavedPath", null);
        t.recordingsCount = d.getInt("recordingsCount", 0);
        return t;
    }

    private static long millis(Instant i) {
        return i == null ? 0 : i.toEpochMilli();
    }

    private static Instant instant(long millis) {
        return millis == 0 ? null : Instant.ofEpochMilli(millis);
    }
}
