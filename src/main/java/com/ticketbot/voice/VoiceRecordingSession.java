package com.ticketbot.voice;

import com.ticketbot.ticket.Ticket;
import net.dv8tion.jda.api.audio.hooks.ConnectionListener;
import net.dv8tion.jda.api.audio.hooks.ConnectionStatus;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.managers.AudioManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * جلسة تسجيل صوتي واحدة.
 *
 * دورة الحياة (State Machine) — كل جلسة تمر بهذه الحالات مرة واحدة فقط وبهذا الترتيب:
 *
 *   CONNECTING  --(CONNECTED)-->  RECORDING  --(stop)-->  STOPPING  -->  STOPPED
 *        \__________________________(stop / error)_________/
 *
 * القاعدة الذهبية التي تمنع الـ Loop:
 *   - openAudioConnection() يُستدعى مرة واحدة فقط في start().
 *   - closeAudioConnection() يُستدعى مرة واحدة فقط في stop().
 *   - لا يوجد أي مكان آخر في البوت يستدعي openAudioConnection().
 *   - بعد STOPPING لا يتم تنفيذ أي شيء من الـ Listener (حتى لو وصلت Events متأخرة).
 */
public final class VoiceRecordingSession {

    private static final Logger LOG = LoggerFactory.getLogger(VoiceRecordingSession.class);

    /** أقصى عدد أخطاء اتصال نسمح بها قبل الإيقاف بدل إعادة المحاولة للأبد. */
    private static final int MAX_CONNECTION_FAILURES = 3;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(20);

    public enum State { CONNECTING, RECORDING, STOPPING, STOPPED }

    /** نتيجة التسجيل بعد الإيقاف. */
    public record Result(Path file, Duration duration, boolean hasAudio, String stopReason) {}

    private final Guild guild;
    private final Ticket ticket;
    private final long voiceChannelId;
    private final long startedById;
    private final WavRecorder recorder;
    private final ScheduledExecutorService scheduler;
    private final AtomicReference<State> state = new AtomicReference<>(State.CONNECTING);
    private final AtomicInteger connectionFailures = new AtomicInteger();
    private final CompletableFuture<Result> done = new CompletableFuture<>();
    private final Instant createdAt = Instant.now();

    /** يُستدعى عندما يحتاج الـ Session أن يطلب من النظام إيقافه (خطأ / طرد / مهلة). */
    private final BiConsumer<VoiceRecordingSession, String> stopRequester;
    /** يُستدعى مرة واحدة عند نجاح الاتصال وبدء التسجيل. */
    private final Consumer<VoiceRecordingSession> onRecordingStarted;

    private volatile ScheduledFuture<?> connectTimeoutTask;
    private volatile ScheduledFuture<?> maxDurationTask;

    VoiceRecordingSession(Guild guild, Ticket ticket, AudioChannel channel, long startedById,
                          Path file, ScheduledExecutorService scheduler,
                          BiConsumer<VoiceRecordingSession, String> stopRequester,
                          Consumer<VoiceRecordingSession> onRecordingStarted) throws java.io.IOException {
        this.guild = guild;
        this.ticket = ticket;
        this.voiceChannelId = channel.getIdLong();
        this.startedById = startedById;
        this.scheduler = scheduler;
        this.stopRequester = stopRequester;
        this.onRecordingStarted = onRecordingStarted;
        this.recorder = new WavRecorder(file);
    }

    /** يفتح الاتصال الصوتي مرة واحدة فقط. */
    void start(AudioChannel channel, int maxMinutes) {
        AudioManager audioManager = guild.getAudioManager();

        // البوت يجب ألا يكون Deafened حتى يستقبل الصوت
        audioManager.setSelfDeafened(false);
        audioManager.setSelfMuted(true);
        audioManager.setReceivingHandler(recorder);
        audioManager.setConnectionListener(new SessionConnectionListener());

        LOG.info("[Voice] Opening ONE audio connection to #{} for {}", channel.getName(), ticket.displayName());
        audioManager.openAudioConnection(channel); // <== المكان الوحيد في البوت كله

        // لو لم يتصل خلال 20 ثانية => نوقف (بدل محاولة الاتصال للأبد)
        connectTimeoutTask = scheduler.schedule(() -> {
            if (state.get() == State.CONNECTING) {
                stopRequester.accept(this, "انتهت مهلة الاتصال بالروم الصوتي");
            }
        }, CONNECT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);

        // حد أقصى لمدة التسجيل
        maxDurationTask = scheduler.schedule(
                () -> stopRequester.accept(this, "تم الوصول للحد الأقصى لمدة التسجيل (" + maxMinutes + " دقيقة)"),
                maxMinutes, TimeUnit.MINUTES);
    }

    /**
     * يوقف التسجيل ويغلق الاتصال. آمن للاستدعاء أكثر من مرة ومن أكثر من Thread:
     * فقط أول استدعاء ينفذ العمل، والباقي يرجع نفس الـ Future.
     */
    CompletableFuture<Result> stop(String reason) {
        State previous = state.getAndSet(State.STOPPING);
        if (previous == State.STOPPING || previous == State.STOPPED) {
            state.set(previous);
            return done; // تم الإيقاف مسبقًا
        }
        LOG.info("[Voice] Stopping session for {} (reason: {})", ticket.displayName(), reason);

        // 1) إلغاء المؤقتات حتى لا تعمل بعد الإيقاف
        cancel(connectTimeoutTask);
        cancel(maxDurationTask);

        // 2) فصل الـ Listener والـ Handler أولاً => أي Event متأخر لن يؤثر علينا
        AudioManager audioManager = guild.getAudioManager();
        audioManager.setConnectionListener(null);
        audioManager.setReceivingHandler(null);

        // 3) إغلاق الاتصال مرة واحدة. هذا أيضًا يلغي أي محاولة إعادة اتصال في طابور JDA.
        audioManager.closeAudioConnection();

        // 4) إنهاء الملف على Virtual Thread (لا نحجز Thread الخاص بـ JDA)
        Thread.ofVirtual().name("voice-finish-" + ticket.number()).start(() -> {
            try {
                Duration duration = recorder.finish();
                done.complete(new Result(recorder.file(), duration, recorder.hasAudio(), reason));
            } catch (Exception e) {
                done.completeExceptionally(e);
            } finally {
                state.set(State.STOPPED);
            }
        });
        return done;
    }

    private static void cancel(ScheduledFuture<?> f) {
        if (f != null) f.cancel(false);
    }

    private final AtomicReference<CompletableFuture<Void>> pipeline = new AtomicReference<>();

    /**
     * يضمن أن عملية "الإيقاف + الرفع + التنظيف" تُنفذ مرة واحدة فقط،
     * حتى لو طُلب الإيقاف من زر "إنهاء التسجيل" ومن Event خروج البوت بنفس الوقت.
     */
    CompletableFuture<Void> pipelineOnce(java.util.function.Supplier<CompletableFuture<Void>> factory) {
        CompletableFuture<Void> placeholder = new CompletableFuture<>();
        if (pipeline.compareAndSet(null, placeholder)) {
            try {
                factory.get().whenComplete((v, e) -> {
                    if (e != null) placeholder.completeExceptionally(e);
                    else placeholder.complete(v);
                });
            } catch (Throwable t) {
                placeholder.completeExceptionally(t);
            }
        }
        return pipeline.get();
    }

    public State state() { return state.get(); }
    public Ticket ticket() { return ticket; }
    public long voiceChannelId() { return voiceChannelId; }
    public long startedById() { return startedById; }
    public long guildId() { return guild.getIdLong(); }
    public Instant createdAt() { return createdAt; }

    /**
     * يراقب حالة الاتصال الصوتي.
     * في الكود القديم لم يكن هناك أي مراقبة، لذلك كان JDA يعيد المحاولة بصمت.
     * هنا: نطبع كل تغيير في الـ Console، ونوقف الجلسة إذا فشل الاتصال أكثر من 3 مرات.
     */
    private final class SessionConnectionListener implements ConnectionListener {
        @Override
        public void onStatusChange(ConnectionStatus status) {
            LOG.info("[Voice] {} -> {}", ticket.displayName(), status);
            State current = state.get();
            if (current == State.STOPPING || current == State.STOPPED) return;

            switch (status) {
                case CONNECTED -> {
                    connectionFailures.set(0);
                    if (state.compareAndSet(State.CONNECTING, State.RECORDING)) {
                        cancel(connectTimeoutTask);
                        recorder.start();
                        onRecordingStarted.accept(VoiceRecordingSession.this);
                    }
                }
                case DISCONNECTED_KICKED_FROM_CHANNEL,
                     DISCONNECTED_CHANNEL_DELETED,
                     DISCONNECTED_LOST_PERMISSION,
                     DISCONNECTED_REMOVED_FROM_GUILD,
                     DISCONNECTED_REMOVED_DURING_RECONNECT,
                     DISCONNECTED_AUTHENTICATION_FAILURE ->
                        stopRequester.accept(VoiceRecordingSession.this, "انقطع الاتصال الصوتي: " + status);
                default -> {
                    // أخطاء يحاول JDA بعدها إعادة الاتصال تلقائيًا (shouldReconnect)
                    if (status.name().startsWith("ERROR_")) {
                        int failures = connectionFailures.incrementAndGet();
                        LOG.warn("[Voice] Connection failure {}/{}: {}", failures, MAX_CONNECTION_FAILURES, status);
                        if (failures >= MAX_CONNECTION_FAILURES) {
                            stopRequester.accept(VoiceRecordingSession.this,
                                    "فشل الاتصال الصوتي عدة مرات (" + status + ") — تحقق من إعداد DAVE");
                        }
                    }
                }
            }
        }
    }
}
