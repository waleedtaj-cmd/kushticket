package com.ticketbot.voice;

import net.dv8tion.jda.api.audio.AudioReceiveHandler;
import net.dv8tion.jda.api.audio.CombinedAudio;
import net.dv8tion.jda.api.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * يستقبل الصوت من JDA ويكتبه في ملف WAV.
 *
 * ===== لماذا Queue + Thread منفصل؟ =====
 * JDA يستدعي handleCombinedAudio كل 20ms من Thread الصوت الخاص به.
 * لو كتبنا على القرص مباشرة داخل هذا الـ Thread وتأخر القرص، سيتأخر استقبال الصوت.
 * لذلك: handleCombinedAudio يضع البيانات في Queue بسرعة،
 * و Virtual Thread (Java 21) منفصل يأخذها ويكتبها في الملف.
 *
 * ===== صيغة الصوت =====
 * JDA يعطينا: 48000Hz, 16-bit, Stereo, Big-Endian.
 * ملف WAV يحتاج Little-Endian. ونحوله إلى Mono لتقليل الحجم للنصف (~5.6MB لكل دقيقة).
 */
public final class WavRecorder implements AudioReceiveHandler {

    private static final Logger LOG = LoggerFactory.getLogger(WavRecorder.class);

    private static final int SAMPLE_RATE = 48_000;
    private static final int CHANNELS = 1;
    private static final int BITS = 16;
    private static final int FRAME_MS = 20;
    private static final int MONO_FRAME_BYTES = SAMPLE_RATE / 1000 * FRAME_MS * 2; // 1920 bytes
    private static final long FRAME_NANOS = TimeUnit.MILLISECONDS.toNanos(FRAME_MS);
    private static final byte[] SILENCE = new byte[MONO_FRAME_BYTES];

    /** قطعة صوت + وقت وصولها (حتى نضيف صمت عند الانقطاع ويبقى التوقيت صحيحًا). */
    private record Chunk(long nanos, byte[] data) {}

    private static final Chunk POISON = new Chunk(0, null); // إشارة "انتهى التسجيل" للـ Thread

    private final Path file;
    private final BlockingQueue<Chunk> queue = new LinkedBlockingQueue<>();
    private final AtomicBoolean accepting = new AtomicBoolean(false);
    private final Thread writerThread;
    private final OutputStream out;

    private volatile long startNanos;
    private long framesWritten;   // يستخدم فقط داخل writerThread
    private long dataBytes;       // يستخدم فقط داخل writerThread
    private volatile IOException writeError;

    public WavRecorder(Path file) throws IOException {
        this.file = file;
        Files.createDirectories(file.getParent());
        this.out = new BufferedOutputStream(Files.newOutputStream(file), 64 * 1024);
        out.write(new byte[44]); // مكان الـ Header، نكتبه في النهاية عندما نعرف الحجم
        this.writerThread = Thread.ofVirtual().name("wav-writer-" + file.getFileName()).unstarted(this::writeLoop);
    }

    /** يبدأ قبول الصوت. يُستدعى مرة واحدة عندما يصبح الاتصال CONNECTED. */
    public void start() {
        if (accepting.compareAndSet(false, true)) {
            startNanos = System.nanoTime();
            writerThread.start();
        }
    }

    // ===================== AudioReceiveHandler =====================

    @Override
    public boolean canReceiveCombined() {
        return true;
    }

    /** نسجل كل البشر في الروم، ونتجاهل البوتات. */
    @Override
    public boolean includeUserInCombinedAudio(User user) {
        return !user.isBot();
    }

    @Override
    public void handleCombinedAudio(CombinedAudio combinedAudio) {
        if (!accepting.get()) return;
        byte[] stereoBigEndian = combinedAudio.getAudioData(1.0);
        queue.offer(new Chunk(System.nanoTime(), toMonoLittleEndian(stereoBigEndian)));
    }

    // ===================== Writer thread =====================

    private void writeLoop() {
        try {
            while (true) {
                Chunk chunk = queue.take();
                if (chunk == POISON) break;
                // إذا انقطع الصوت (لا أحد يتكلم أو إعادة اتصال) نضيف صمتًا حتى يبقى التوقيت حقيقيًا
                long expectedFrames = (chunk.nanos() - startNanos) / FRAME_NANOS;
                while (framesWritten < expectedFrames - 1) {
                    write(SILENCE);
                }
                write(chunk.data());
            }
            out.flush();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            writeError = e;
            LOG.error("Failed writing recording {}", file, e);
        }
    }

    private void write(byte[] data) throws IOException {
        out.write(data);
        dataBytes += data.length;
        framesWritten++;
    }

    /**
     * يوقف التسجيل ويكمل الملف. آمن للاستدعاء أكثر من مرة.
     * @return مدة التسجيل
     */
    public Duration finish() throws IOException {
        boolean wasAccepting = accepting.getAndSet(false);
        if (wasAccepting) {
            queue.offer(POISON);
            try {
                writerThread.join(Duration.ofSeconds(15));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        out.close();
        if (writeError != null) throw writeError;
        writeHeader();
        return Duration.ofMillis(dataBytes * 1000L / (SAMPLE_RATE * CHANNELS * (BITS / 8)));
    }

    public boolean hasAudio() {
        return dataBytes > 0;
    }

    public Path file() {
        return file;
    }

    /** يكتب WAV header (44 bytes) في بداية الملف. */
    private void writeHeader() throws IOException {
        int byteRate = SAMPLE_RATE * CHANNELS * BITS / 8;
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
            raf.seek(0);
            raf.writeBytes("RIFF");
            raf.writeInt(Integer.reverseBytes((int) (36 + dataBytes)));
            raf.writeBytes("WAVE");
            raf.writeBytes("fmt ");
            raf.writeInt(Integer.reverseBytes(16));                         // PCM chunk size
            raf.writeShort(Short.reverseBytes((short) 1));                  // PCM format
            raf.writeShort(Short.reverseBytes((short) CHANNELS));
            raf.writeInt(Integer.reverseBytes(SAMPLE_RATE));
            raf.writeInt(Integer.reverseBytes(byteRate));
            raf.writeShort(Short.reverseBytes((short) (CHANNELS * BITS / 8))); // block align
            raf.writeShort(Short.reverseBytes((short) BITS));
            raf.writeBytes("data");
            raf.writeInt(Integer.reverseBytes((int) dataBytes));
        }
    }

    /** Stereo 16-bit Big-Endian -> Mono 16-bit Little-Endian. */
    private static byte[] toMonoLittleEndian(byte[] in) {
        byte[] out = new byte[in.length / 2];
        for (int i = 0, o = 0; i + 3 < in.length; i += 4, o += 2) {
            short left = (short) (((in[i] & 0xFF) << 8) | (in[i + 1] & 0xFF));
            short right = (short) (((in[i + 2] & 0xFF) << 8) | (in[i + 3] & 0xFF));
            int mono = (left + right) / 2;
            out[o] = (byte) (mono & 0xFF);
            out[o + 1] = (byte) ((mono >> 8) & 0xFF);
        }
        return out;
    }
}
