package com.ticketbot.ticket;

import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * تخزين التذاكر في ملف data/tickets.json.
 *
 * لماذا؟ لو أعدت تشغيل البوت، يجب أن يتذكر من عمل Claim لكل تذكرة،
 * وما هو ID رسالة التحكم، وكم نسخة Transcript تم حفظها.
 *
 * نستخدم DataObject/DataArray الموجودة داخل JDA نفسها، فلا نحتاج مكتبة JSON إضافية.
 */
public final class TicketStore {

    private static final Logger LOG = LoggerFactory.getLogger(TicketStore.class);

    private final Path file;
    private final Map<Long, Ticket> byChannel = new ConcurrentHashMap<>();
    private final AtomicInteger counter = new AtomicInteger();

    private TicketStore(Path file) {
        this.file = file;
    }

    public static TicketStore load(Path dataDir) throws IOException {
        Files.createDirectories(dataDir);
        TicketStore store = new TicketStore(dataDir.resolve("tickets.json"));
        if (Files.exists(store.file)) {
            try (InputStream in = Files.newInputStream(store.file)) {
                DataObject root = DataObject.fromJson(in);
                store.counter.set(root.getInt("counter", 0));
                DataArray arr = root.getArray("tickets");
                for (int i = 0; i < arr.length(); i++) {
                    Ticket t = Ticket.fromData(arr.getObject(i));
                    store.byChannel.put(t.channelId(), t);
                }
            }
            LOG.info("Loaded {} tickets from {}", store.byChannel.size(), store.file.toAbsolutePath());
        }
        return store;
    }

    /**
     * حفظ آمن: نكتب في ملف مؤقت ثم ننقله فوق الملف الأصلي.
     * لو انطفأ الجهاز أثناء الكتابة، لن يتلف الملف الأصلي.
     */
    public synchronized void save() {
        try {
            DataArray arr = DataArray.empty();
            for (Ticket t : byChannel.values()) {
                arr.add(t.toData());
            }
            DataObject root = DataObject.empty()
                    .put("counter", counter.get())
                    .put("tickets", arr);
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, root.toPrettyString(), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.error("Failed to save tickets.json", e);
        }
    }

    public int nextNumber() {
        return counter.incrementAndGet();
    }

    public void add(Ticket ticket) {
        byChannel.put(ticket.channelId(), ticket);
        save();
    }

    public Optional<Ticket> byChannel(long channelId) {
        return Optional.ofNullable(byChannel.get(channelId));
    }

    /** هل لدى هذا العضو تذكرة مفتوحة من نفس النوع؟ (لمنع فتح 10 تذاكر بنفس الوقت) */
    public Optional<Ticket> findOpen(long guildId, long ownerId, TicketType type) {
        return byChannel.values().stream()
                .filter(t -> t.guildId() == guildId && t.ownerId() == ownerId && t.type() == type)
                .filter(t -> t.status() == Ticket.Status.OPEN)
                .findFirst();
    }

    public Collection<Ticket> all() {
        return byChannel.values();
    }
}
