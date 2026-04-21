package com.oussama_chatri.productivityx.features.notes.jobs;

import com.oussama_chatri.productivityx.features.notes.repository.NoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Permanently removes notes that have been in the trash for more than 30 days.
 * Runs nightly at 04:00 UTC — offset from TokenCleanupJob (03:00) to avoid
 * competing transactions on the same database.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TrashPurgeJob {

    private static final int TRASH_RETENTION_DAYS = 30;

    private final NoteRepository noteRepository;

    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void purgeExpiredTrash() {
        Instant cutoff = Instant.now().minus(TRASH_RETENTION_DAYS, ChronoUnit.DAYS);
        int deleted = noteRepository.purgeTrash(cutoff);
        log.info("Trash purge complete — permanently deleted {} notes older than {} days",
                deleted, TRASH_RETENTION_DAYS);
    }
}