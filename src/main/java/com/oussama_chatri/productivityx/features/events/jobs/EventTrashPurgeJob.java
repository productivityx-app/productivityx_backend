package com.oussama_chatri.productivityx.features.events.jobs;

import com.oussama_chatri.productivityx.features.events.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Permanently removes events that have been in the trash for more than 30 days.
 * Runs nightly at 05:00 UTC — offset from other purge jobs to avoid
 * overlapping transactions on the same database connection pool.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventTrashPurgeJob {

    private static final int TRASH_RETENTION_DAYS = 30;

    private final EventRepository eventRepository;

    @Scheduled(cron = "0 0 5 * * *")
    @Transactional
    public void purgeExpiredTrash() {
        Instant cutoff = Instant.now().minus(TRASH_RETENTION_DAYS, ChronoUnit.DAYS);
        int deleted = eventRepository.purgeTrash(cutoff);
        log.info("Event trash purge complete — permanently deleted {} events older than {} days",
                deleted, TRASH_RETENTION_DAYS);
    }
}
