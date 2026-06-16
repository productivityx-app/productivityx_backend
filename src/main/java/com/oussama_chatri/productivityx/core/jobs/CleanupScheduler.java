package com.oussama_chatri.productivityx.core.jobs;

import com.oussama_chatri.productivityx.features.events.repository.EventRepository;
import com.oussama_chatri.productivityx.features.notes.repository.NoteRepository;
import com.oussama_chatri.productivityx.features.tasks.repository.TaskRepository;
import com.oussama_chatri.productivityx.shared.websocket.WebSocketNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Nightly cleanup of soft-deleted tombstones older than the retention window.
 *
 * <p>Soft-deleted items accumulate over time and slow queries, bloat indexes,
 * and grow backup sizes. The 30-day retention gives users a recycle-bin experience
 * while keeping the table lean. After cleanup, each affected user receives a
 * WebSocket push so their client removes the purged items from local Room storage.
 *
 * <p>Runs at 03:00 UTC — after TokenCleanupJob (03:00 tokens, 04:00+ trash purges)
 * but before peak traffic. Each table is processed in its own transaction with
 * a batch size of 500 rows to avoid long-running TXs that could stall replication.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CleanupScheduler {

    /** Retention window — items trashed longer than this are permanently removed. */
    private static final int RETENTION_DAYS = 30;

    /** Max rows deleted per table per TX batch — keeps each TX short. */
    private static final int BATCH_SIZE = 500;

    private final NoteRepository  noteRepository;
    private final TaskRepository  taskRepository;
    private final EventRepository eventRepository;
    private final WebSocketNotifier wsNotifier;

    /**
     * Scheduled entry point — runs daily at 03:00 UTC.
     * Processes notes, then tasks, then events. Each table gets its own TX.
     */
    @Scheduled(cron = "0 3 * * * *")
    public void purgeDeletedRecords() {
        log.info("Tombstone cleanup started — retention={}d", RETENTION_DAYS);
        Instant cutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);

        int notesPurged  = purgeTable("notes",  cutoff, noteRepository::purgeTrashReturning);
        int tasksPurged  = purgeTable("tasks",  cutoff, taskRepository::purgeTrashReturning);
        int eventsPurged = purgeTable("events", cutoff, eventRepository::purgeTrashReturning);

        int total = notesPurged + tasksPurged + eventsPurged;
        log.info("Tombstone cleanup complete — notes={} tasks={} events={} total={}",
                notesPurged, tasksPurged, eventsPurged, total);
    }

    /**
     * Purges one table in repeated batches of {@link #BATCH_SIZE} until no
     * qualifying rows remain. Each batch runs in its own @Transactional method
     * so no single TX grows beyond 500 deletes.
     *
     * After each batch, WebSocket notifications are sent so clients can evict
     * the purged IDs from local storage immediately.
     */
    private int purgeTable(String tableName, Instant cutoff, PurgeBatch purgeBatch) {
        int totalPurged = 0;
        int batchCount;

        do {
            batchCount = purgeOneBatch(cutoff, purgeBatch, tableName);
            totalPurged += batchCount;
        } while (batchCount == BATCH_SIZE); // full batch = maybe more remain

        return totalPurged;
    }

    @Transactional
    protected int purgeOneBatch(Instant cutoff, PurgeBatch purgeBatch, String tableName) {
        List<Object[]> rows = purgeBatch.execute(cutoff);

        if (rows.isEmpty()) {
            return 0;
        }

        // Group purged IDs by userId so we send one WS message per user
        Map<UUID, List<UUID>> byUser = rows.stream()
                .collect(Collectors.groupingBy(
                        r -> (UUID) r[1],
                        Collectors.mapping(r -> (UUID) r[0], Collectors.toList())
                ));

        String wsEventType = tableName + ".purged";
        Instant purgedAt = Instant.now();

        byUser.forEach((userId, ids) -> {
            for (UUID id : ids) {
                wsNotifier.notifyUser(userId, wsEventType,
                        new PurgePayload(id, purgedAt));
            }
        });

        return rows.size();
    }

    /** Functional interface for the per-table purge batch call. */
    @FunctionalInterface
    private interface PurgeBatch {
        List<Object[]> execute(Instant cutoff);
    }

    /** Lightweight payload sent over WebSocket so clients evict purged items. */
    private record PurgePayload(UUID id, Instant purgedAt) {}
}
