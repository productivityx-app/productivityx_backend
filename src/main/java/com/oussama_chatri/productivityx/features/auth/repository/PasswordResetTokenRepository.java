package com.oussama_chatri.productivityx.features.auth.repository;

import com.oussama_chatri.productivityx.features.auth.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHashAndUsedAtIsNull(String tokenHash);

    // Looks up the most recent active reset token for a user matching the OTP submitted
    @Query("""
            SELECT t FROM PasswordResetToken t
            WHERE t.user.id = :userId
              AND t.otp = :otp
              AND t.usedAt IS NULL
            ORDER BY t.createdAt DESC
            LIMIT 1
            """)
    Optional<PasswordResetToken> findLatestActiveOtpForUser(
            @Param("userId") UUID userId,
            @Param("otp") String otp);

    // Fetches the most recent unused token for a user regardless of OTP —
    // used to increment the attempt counter on wrong OTP submissions
    @Query("""
            SELECT t FROM PasswordResetToken t
            WHERE t.user.id = :userId
              AND t.usedAt IS NULL
            ORDER BY t.createdAt DESC
            LIMIT 1
            """)
    Optional<PasswordResetToken> findLatestActiveTokenForUser(@Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt < :cutoff")
    int deleteExpiredTokens(@Param("cutoff") Instant cutoff);
}