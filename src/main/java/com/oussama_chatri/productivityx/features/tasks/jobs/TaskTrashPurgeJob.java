package com.oussama_chatri.productivityx.features.tasks.jobs;

import com.oussama_chatri.productivityx.features.tasks.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Permanently removes tasks that have been in the trash for more than 30 days.
 * Runs nightly at 04:30 UTC — offset from TrashPurgeJob (04:00) and
 * TokenCleanupJob (03:00) to avoid competing transactions.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskTrashPurgeJob {

    private static final int TRASH_RETENTION_DAYS = 30;

    private final TaskRepository taskRepository;

    @Scheduled(cron = "0 30 4 * * *")
    @Transactional
    public void purgeExpiredTrash() {
        Instant cutoff = Instant.now().minus(TRASH_RETENTION_DAYS, ChronoUnit.DAYS);
        int deleted = taskRepository.purgeTrash(cutoff);
        log.info("Task trash purge complete — permanently deleted {} tasks older than {} days",
                deleted, TRASH_RETENTION_DAYS);
    }
}
