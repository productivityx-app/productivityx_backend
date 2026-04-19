package com.oussama_chatri.productivityx.features.auth.repository;

import com.oussama_chatri.productivityx.features.auth.entity.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findByTokenHashAndUsedAtIsNull(String tokenHash);

    // Scoped to a specific user to prevent OTP enumeration across accounts
    @Query("""
            SELECT t FROM EmailVerificationToken t
            WHERE t.user.id = :userId
              AND t.otp = :otp
              AND t.usedAt IS NULL
            ORDER BY t.createdAt DESC
            LIMIT 1
            """)
    Optional<EmailVerificationToken> findLatestActiveOtpForUser(
            @Param("userId") UUID userId,
            @Param("otp") String otp);

    // Fetches the most recent unused token for a user regardless of OTP value —
    // used to increment the attempt counter when a wrong OTP is submitted
    @Query("""
            SELECT t FROM EmailVerificationToken t
            WHERE t.user.id = :userId
              AND t.usedAt IS NULL
            ORDER BY t.createdAt DESC
            LIMIT 1
            """)
    Optional<EmailVerificationToken> findLatestActiveTokenForUser(@Param("userId") UUID userId);

    // Cleanup — used by TokenCleanupJob
    @Modifying
    @Query("DELETE FROM EmailVerificationToken t WHERE t.expiresAt < :cutoff")
    int deleteExpiredTokens(@Param("cutoff") Instant cutoff);
}