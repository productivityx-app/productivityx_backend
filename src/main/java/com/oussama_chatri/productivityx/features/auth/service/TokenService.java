package com.oussama_chatri.productivityx.features.auth.service;

import com.oussama_chatri.productivityx.core.exception.AppException;
import com.oussama_chatri.productivityx.core.exception.ErrorCode;
import com.oussama_chatri.productivityx.core.security.JwtService;
import com.oussama_chatri.productivityx.core.user.User;
import com.oussama_chatri.productivityx.core.util.CryptoUtils;
import com.oussama_chatri.productivityx.features.auth.dto.response.AuthResponse;
import com.oussama_chatri.productivityx.features.auth.entity.EmailVerificationToken;
import com.oussama_chatri.productivityx.features.auth.entity.PasswordResetToken;
import com.oussama_chatri.productivityx.features.auth.entity.RefreshToken;
import com.oussama_chatri.productivityx.features.auth.repository.EmailVerificationTokenRepository;
import com.oussama_chatri.productivityx.features.auth.repository.PasswordResetTokenRepository;
import com.oussama_chatri.productivityx.features.auth.repository.RefreshTokenRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

/**
 * Owns the full lifecycle of all token types:
 * refresh tokens, email verification tokens, and password reset tokens.
 *
 * Email verification tokens expose the raw value via a transient field so
 * callers can embed it in the verification URL without ever seeing the hash.
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final String REFRESH_COOKIE_NAME = "refreshToken";

    private final RefreshTokenRepository           refreshTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository     passwordResetTokenRepository;
    private final JwtService                       jwtService;
    private final CryptoUtils                      cryptoUtils;

    @Value("${app.jwt.access-expiry-ms:900000}")
    private long accessExpiryMs;

    @Value("${app.jwt.refresh-expiry-days:7}")
    private long refreshExpiryDays;

    // Access token

    public String createAccessToken(User user) {
        return jwtService.generateAccessToken(user.getId(), user.getEmail());
    }

    public long accessExpiryMs() {
        return accessExpiryMs;
    }

    // Refresh token

    @Transactional
    public AuthResponse issueTokenPair(User user, String ip, String deviceInfo,
                                       HttpServletResponse response) {
        String accessToken = createAccessToken(user);
        String raw = generateSecureRandom();
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(cryptoUtils.sha256Hex(raw))
                .deviceInfo(deviceInfo)
                .ipAddress(ip)
                .expiresAt(Instant.now().plus(refreshExpiryDays, ChronoUnit.DAYS))
                .build());
        setRefreshCookie(raw, response);
        return AuthResponse.of(accessToken, accessExpiryMs);
    }

    @Transactional
    public RefreshToken rotateRefreshToken(String raw, HttpServletResponse response) {
        String hash = cryptoUtils.sha256Hex(raw);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> AppException.unauthorized(ErrorCode.AUTH_TOKEN_INVALID));

        if (token.getRevokedAt() != null) {
            // Token reuse detected — revoke the entire family for this user
            refreshTokenRepository.revokeAllForUser(token.getUser().getId(), Instant.now());
            throw AppException.unauthorized(ErrorCode.AUTH_REFRESH_TOKEN_REVOKED);
        }
        if (Instant.now().isAfter(token.getExpiresAt())) {
            throw AppException.unauthorized(ErrorCode.AUTH_TOKEN_EXPIRED);
        }

        token.setRevokedAt(Instant.now());
        refreshTokenRepository.save(token);

        String newRaw = generateSecureRandom();
        refreshTokenRepository.save(RefreshToken.builder()
                .user(token.getUser())
                .tokenHash(cryptoUtils.sha256Hex(newRaw))
                .deviceInfo(token.getDeviceInfo())
                .ipAddress(token.getIpAddress())
                .expiresAt(Instant.now().plus(refreshExpiryDays, ChronoUnit.DAYS))
                .build());
        setRefreshCookie(newRaw, response);
        return token;
    }

    @Transactional
    public void revokeRefreshToken(String raw) {
        if (raw == null || raw.isBlank()) return;
        refreshTokenRepository.findByTokenHash(cryptoUtils.sha256Hex(raw)).ifPresent(t -> {
            t.setRevokedAt(Instant.now());
            refreshTokenRepository.save(t);
        });
    }

    @Transactional
    public void revokeAllRefreshTokens(UUID userId) {
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
    }

    // Email verification tokens

    /**
     * Creates and persists the verification token.
     * The returned entity carries the raw (unhashed) token in a transient field
     * so the caller can embed it in the magic-link URL. The hash is what is stored.
     */
    @Transactional
    public EmailVerificationToken createEmailVerificationToken(User user, String otp) {
        String raw = generateSecureRandom();
        EmailVerificationToken saved = emailVerificationTokenRepository.save(
                EmailVerificationToken.builder()
                        .user(user)
                        .tokenHash(cryptoUtils.sha256Hex(raw))
                        .otp(otp)
                        .otpAttempts(0)
                        .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                        .build());
        // Attach the raw value transiently — never persisted, safe for the caller to use in URLs
        saved.setRawToken(raw);
        return saved;
    }

    // Password reset tokens

    @Transactional
    public String createPasswordResetToken(User user) {
        String raw = generateSecureRandom();
        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .user(user)
                .tokenHash(cryptoUtils.sha256Hex(raw))
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build());
        return raw;
    }

    // Cookie helpers

    public void setRefreshCookie(String rawToken, HttpServletResponse response) {
        // Servlet Cookie API does not expose SameSite — set via raw header
        response.addHeader("Set-Cookie",
                REFRESH_COOKIE_NAME + "=" + rawToken
                        + "; Path=/api/v1/auth"
                        + "; Max-Age=" + (refreshExpiryDays * 24 * 3600)
                        + "; HttpOnly; Secure; SameSite=Strict");
    }

    public void clearRefreshCookie(HttpServletResponse response) {
        response.addHeader("Set-Cookie",
                REFRESH_COOKIE_NAME + "="
                        + "; Path=/api/v1/auth"
                        + "; Max-Age=0"
                        + "; HttpOnly; Secure; SameSite=Strict");
    }

    // Internal

    private String generateSecureRandom() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}