package com.oussama_chatri.productivityx.features.auth.entity;

import com.oussama_chatri.productivityx.core.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_verification_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, length = 255)
    private String tokenHash;

    // 6-digit OTP for in-app verification — same TTL as the magic-link token
    @Column(nullable = false, length = 6)
    private String otp;

    @Builder.Default
    @Column(name = "otp_attempts", nullable = false)
    private Integer otpAttempts = 0;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    /**
     * Transient — never persisted.
     * TokenService sets this immediately after save so callers can build the magic-link URL
     * without regenerating the raw value or touching the hash.
     */
    @Transient
    private String rawToken;
}