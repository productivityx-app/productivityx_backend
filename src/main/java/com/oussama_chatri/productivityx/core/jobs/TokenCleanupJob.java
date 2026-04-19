package com.oussama_chatri.productivityx.core.jobs;

import com.oussama_chatri.productivityx.features.auth.repository.EmailVerificationTokenRepository;
import com.oussama_chatri.productivityx.features.auth.repository.PasswordResetTokenRepository;
import com.oussama_chatri.productivityx.features.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Nightly purge of expired auth tokens.
 * Without this, the three token tables grow unbounded — every registration,
 * every password reset, and every refresh cycle adds rows that are never deleted.
 * Runs at 03:00 UTC to avoid overlapping with peak traffic.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TokenCleanupJob {

    private final RefreshTokenRepository           refreshTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository     passwordResetTokenRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        Instant cutoff = Instant.now();

        int refreshDeleted = refreshTokenRepository.deleteExpiredTokens(cutoff);
        int evDeleted      = emailVerificationTokenRepository.deleteExpiredTokens(cutoff);
        int prDeleted      = passwordResetTokenRepository.deleteExpiredTokens(cutoff);

        log.info("Token cleanup complete — refresh={} emailVerification={} passwordReset={}",
                refreshDeleted, evDeleted, prDeleted);
    }
}