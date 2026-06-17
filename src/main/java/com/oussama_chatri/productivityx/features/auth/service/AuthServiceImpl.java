package com.oussama_chatri.productivityx.features.auth.service;

import com.oussama_chatri.productivityx.core.audit.AuditEvent;
import com.oussama_chatri.productivityx.core.audit.AuditService;
import com.oussama_chatri.productivityx.core.enums.Platform;
import com.oussama_chatri.productivityx.core.exception.AppException;
import com.oussama_chatri.productivityx.core.exception.ErrorCode;
import com.oussama_chatri.productivityx.core.user.User;
import com.oussama_chatri.productivityx.core.user.UserRepository;
import com.oussama_chatri.productivityx.core.util.CryptoUtils;
import com.oussama_chatri.productivityx.core.util.SecurityUtils;
import com.oussama_chatri.productivityx.core.util.StringUtils;
import com.oussama_chatri.productivityx.core.util.ValidationUtils;
import com.oussama_chatri.productivityx.features.auth.dto.request.*;
import com.oussama_chatri.productivityx.features.auth.dto.response.AuthResponse;
import com.oussama_chatri.productivityx.features.auth.dto.response.ForgotPasswordOtpVerifiedResponse;
import com.oussama_chatri.productivityx.features.auth.dto.response.UserResponse;
import com.oussama_chatri.productivityx.features.auth.entity.EmailVerificationToken;
import com.oussama_chatri.productivityx.features.auth.entity.PasswordResetToken;
import com.oussama_chatri.productivityx.features.auth.entity.RefreshToken;
import com.oussama_chatri.productivityx.features.auth.entity.UserDevice;
import com.oussama_chatri.productivityx.features.auth.repository.EmailVerificationTokenRepository;
import com.oussama_chatri.productivityx.features.auth.repository.PasswordResetTokenRepository;
import com.oussama_chatri.productivityx.features.auth.repository.UserDeviceRepository;
import com.oussama_chatri.productivityx.features.preferences.entity.UserPreferences;
import com.oussama_chatri.productivityx.features.preferences.repository.UserPreferencesRepository;
import com.oussama_chatri.productivityx.features.profile.entity.Profile;
import com.oussama_chatri.productivityx.features.profile.repository.ProfileRepository;
import com.oussama_chatri.productivityx.shared.email.EmailService;
import com.oussama_chatri.productivityx.shared.ratelimit.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private static final int MAX_FAILED_LOGINS = 5;
    private static final long LOCK_DURATION_MINUTES = 15L;
    private static final int OTP_MAX_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final UserPreferencesRepository preferencesRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SecurityUtils securityUtils;
    private final ValidationUtils validationUtils;
    private final RateLimiterService rateLimiterService;
    private final TokenService tokenService;
    private final AuditService auditService;
    private final CryptoUtils cryptoUtils;
    private final StringUtils stringUtils;

    @Value("${app.jwt.access-expiry-ms:900000}")
    private long accessExpiryMs;

    @Value("${app.base-url}")
    private String baseUrl;

    // Registration

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw AppException.conflict(ErrorCode.AUTH_DUPLICATE_EMAIL);
        }
        if (request.getUsername() != null && userRepository.existsByUsername(request.getUsername())) {
            throw AppException.conflict(ErrorCode.AUTH_DUPLICATE_USERNAME);
        }
        if (request.getPhone() != null && userRepository.existsByPhone(request.getPhone())) {
            throw AppException.conflict(ErrorCode.AUTH_DUPLICATE_PHONE);
        }
        if (!validationUtils.isValidEmail(request.getEmail())) {
            throw AppException.badRequest(ErrorCode.VAL_INVALID_EMAIL);
        }
        if (!validationUtils.isValidPassword(request.getPassword())) {
            throw AppException.badRequest(ErrorCode.VAL_WEAK_PASSWORD);
        }
        if (request.getBirthDate() != null && !validationUtils.meetsMinimumAge(request.getBirthDate(), 13)) {
            throw AppException.badRequest(ErrorCode.AUTH_UNDERAGE);
        }

        User user = userRepository.save(User.builder()
                .email(request.getEmail().toLowerCase().trim())
                .username(request.getUsername())
                .phone(request.getPhone())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .gender(request.getGender())
                .birthDate(request.getBirthDate())
                .build());

        profileRepository.save(Profile.builder()
                .user(user)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .build());

        preferencesRepository.save(UserPreferences.builder().user(user).build());

        String otp = generateOtp();
        EmailVerificationToken evToken = tokenService.createEmailVerificationToken(user, otp);
        String verificationUrl = baseUrl + "/api/v1/auth/verify-email?token=" + evToken.getRawToken();
        emailService.sendVerificationEmail(user.getEmail(), request.getFirstName(), verificationUrl, otp);

        auditService.log(AuditEvent.REGISTER, user.getId(), null,
                stringUtils.maskEmail(user.getEmail()));
        log.info("Registered: {}", stringUtils.maskEmail(user.getEmail()));
    }

    // Verify email via magic link

    @Override
    @Transactional
    public AuthResponse verifyEmail(String rawToken, HttpServletResponse response) {
        String hash = cryptoUtils.sha256Hex(rawToken);

        EmailVerificationToken tokenEntity = emailVerificationTokenRepository
                .findByTokenHashAndUsedAtIsNull(hash)
                .orElseThrow(() -> AppException.unauthorized(ErrorCode.AUTH_TOKEN_INVALID));

        if (Instant.now().isAfter(tokenEntity.getExpiresAt())) {
            throw AppException.unauthorized(ErrorCode.AUTH_TOKEN_EXPIRED);
        }

        return completeVerification(tokenEntity, response);
    }

    // Verify email via 6-digit OTP

    @Override
    @Transactional
    public AuthResponse verifyOtp(VerifyOtpRequest request, HttpServletResponse response) {
        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> AppException.unauthorized(ErrorCode.AUTH_TOKEN_INVALID));

        EmailVerificationToken token = emailVerificationTokenRepository
                .findLatestActiveOtpForUser(user.getId(), request.getOtp())
                .orElseThrow(() -> {
                    incrementOtpAttempt(user.getId());
                    return AppException.unauthorized(ErrorCode.AUTH_TOKEN_INVALID);
                });

        if (token.getOtpAttempts() >= OTP_MAX_ATTEMPTS) {
            throw AppException.rateLimited(ErrorCode.RATE_OTP_EXCEEDED);
        }
        if (Instant.now().isAfter(token.getExpiresAt())) {
            throw AppException.unauthorized(ErrorCode.AUTH_TOKEN_EXPIRED);
        }

        return completeVerification(token, response);
    }

    private AuthResponse completeVerification(EmailVerificationToken token, HttpServletResponse response) {
        User user = token.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        token.setUsedAt(Instant.now());
        emailVerificationTokenRepository.save(token);

        auditService.log(AuditEvent.EMAIL_VERIFIED, user.getId(), null);
        log.info("Email verified: {}", stringUtils.maskEmail(user.getEmail()));

        return tokenService.issueTokenPair(user, null, null, null, response);
    }

    private void incrementOtpAttempt(UUID userId) {
        emailVerificationTokenRepository.findLatestActiveTokenForUser(userId).ifPresent(t -> {
            t.setOtpAttempts(t.getOtpAttempts() + 1);
            emailVerificationTokenRepository.save(t);
        });
    }

    // Resend verification

    @Override
    @Transactional
    public void resendVerification(ResendVerificationRequest request) {
        if (!validationUtils.isValidEmail(request.getEmail())) {
            throw AppException.badRequest(ErrorCode.VAL_INVALID_EMAIL);
        }

        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim()).orElse(null);
        if (user == null) {
            log.debug("Resend verification requested for unknown email");
            return;
        }
        if (user.isEmailVerified()) {
            throw AppException.badRequest(ErrorCode.AUTH_EMAIL_ALREADY_VERIFIED);
        }

        rateLimiterService.assertResendAllowed(user.getId());

        String otp = generateOtp();
        EmailVerificationToken evToken = tokenService.createEmailVerificationToken(user, otp);
        String verificationUrl = baseUrl + "/api/v1/auth/verify-email?token=" + evToken.getRawToken();

        Profile profile = profileRepository.findByUserId(user.getId()).orElse(null);
        String firstName = profile != null ? profile.getFirstName() : null;
        emailService.sendVerificationEmail(user.getEmail(), firstName, verificationUrl, otp);
    }

    // Login with device tracking

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        String clientIp = extractClientIp(httpRequest);
        String identifier = request.getIdentifier().trim();

        User user = userRepository.findByIdentifier(identifier)
                .orElseThrow(() -> AppException.unauthorized(ErrorCode.AUTH_USER_NOT_FOUND));

        if (!user.isEmailVerified()) {
            throw AppException.forbidden(ErrorCode.AUTH_EMAIL_NOT_VERIFIED);
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            auditService.log(AuditEvent.ACCOUNT_LOCKED, user.getId(), clientIp);
            throw AppException.forbidden(ErrorCode.AUTH_ACCOUNT_LOCKED);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            handleFailedLogin(user, clientIp);
            throw AppException.unauthorized(ErrorCode.AUTH_WRONG_PASSWORD);
        }

        // Reset failed login counter on success
        if (user.getFailedLoginCount() > 0) {
            user.setFailedLoginCount(0);
        }
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        // Upsert device record for multi-device session tracking
        String deviceId = request.getDeviceId();
        String deviceName = request.getDeviceName();
        Platform platform = parsePlatform(request.getPlatform());
        if (deviceId != null && !deviceId.isBlank()) {
            upsertDevice(user, deviceId, deviceName, platform);
        }

        auditService.log(AuditEvent.LOGIN_SUCCESS, user.getId(), clientIp, deviceName);
        log.info("Login success userId={} device={} platform={}", user.getId(), deviceName, platform);

        return tokenService.issueTokenPair(user, clientIp, deviceName, deviceId, response);
    }

    // Refresh with device binding check

    @Override
    @Transactional
    public AuthResponse refresh(String rawToken, String deviceId, HttpServletResponse response) {
        RefreshToken oldToken = tokenService.rotateRefreshToken(rawToken, deviceId, response);
        User user = oldToken.getUser();

        // Update device last-seen timestamp
        if (deviceId != null && oldToken.getDeviceId() != null) {
            userDeviceRepository.updateLastSeen(user.getId(), oldToken.getDeviceId(), Instant.now());
        }

        String accessToken = tokenService.createAccessToken(user);
        return AuthResponse.of(accessToken, tokenService.accessExpiryMs());
    }

    // Logout

    @Override
    @Transactional
    public void logout(String rawToken, HttpServletResponse response) {
        tokenService.revokeRefreshToken(rawToken);
        tokenService.clearRefreshCookie(response);
        log.debug("Logout completed");
    }

    // Forgot password

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        if (!validationUtils.isValidEmail(request.getEmail())) {
            throw AppException.badRequest(ErrorCode.VAL_INVALID_EMAIL);
        }

        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim()).orElse(null);
        if (user == null) {
            log.debug("Forgot password requested for unknown email");
            return;
        }

        rateLimiterService.assertResendAllowed(user.getId());

        String otp = generateOtp();
        PasswordResetToken prToken = tokenService.createPasswordResetToken(user, otp);
        String resetUrl = baseUrl + "/api/v1/auth/reset-password?token=" + prToken.getRawToken();

        Profile profile = profileRepository.findByUserId(user.getId()).orElse(null);
        String firstName = profile != null ? profile.getFirstName() : null;
        emailService.sendPasswordResetEmail(user.getEmail(), firstName, resetUrl, otp);
    }

    @Override
    @Transactional
    public ForgotPasswordOtpVerifiedResponse verifyForgotPasswordOtp(VerifyForgotPasswordOtpRequest request) {
        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> AppException.unauthorized(ErrorCode.AUTH_USER_NOT_FOUND));

        PasswordResetToken token = passwordResetTokenRepository
                .findLatestActiveOtpForUser(user.getId(), request.getOtp())
                .orElseThrow(() -> AppException.unauthorized(ErrorCode.AUTH_TOKEN_INVALID));

        if (token.getOtpAttempts() >= OTP_MAX_ATTEMPTS) {
            throw AppException.rateLimited(ErrorCode.RATE_OTP_EXCEEDED);
        }
        if (Instant.now().isAfter(token.getExpiresAt())) {
            throw AppException.unauthorized(ErrorCode.AUTH_TOKEN_EXPIRED);
        }

        // Issue a short-lived reset token
        PasswordResetToken resetToken = tokenService.createPasswordResetToken(user, null);
        return ForgotPasswordOtpVerifiedResponse.builder()
                .resetToken(resetToken.getRawToken())
                .build();
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String hash = cryptoUtils.sha256Hex(request.getToken());

        PasswordResetToken token = passwordResetTokenRepository
                .findByTokenHashAndUsedAtIsNull(hash)
                .orElseThrow(() -> AppException.unauthorized(ErrorCode.AUTH_TOKEN_INVALID));

        if (Instant.now().isAfter(token.getExpiresAt())) {
            throw AppException.unauthorized(ErrorCode.AUTH_TOKEN_EXPIRED);
        }

        if (!validationUtils.isValidPassword(request.getNewPassword())) {
            throw AppException.badRequest(ErrorCode.VAL_WEAK_PASSWORD);
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        token.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(token);

        // Revoke all sessions after password reset
        tokenService.revokeAllRefreshTokens(user.getId());

        auditService.log(AuditEvent.PASSWORD_RESET, user.getId(), null);
        log.info("Password reset for userId={}", user.getId());
    }

    // Change password

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = securityUtils.currentUser();

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw AppException.badRequest(ErrorCode.AUTH_PASSWORD_MISMATCH);
        }
        if (!validationUtils.isValidPassword(request.getNewPassword())) {
            throw AppException.badRequest(ErrorCode.VAL_WEAK_PASSWORD);
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        auditService.log(AuditEvent.PASSWORD_CHANGED, user.getId(), null);
    }

    // Me

    @Override
    @Transactional(readOnly = true)
    public UserResponse me() {
        User user = securityUtils.currentUser();
        Profile profile = profileRepository.findByUserId(user.getId())
                .orElseThrow(() -> AppException.notFound(ErrorCode.RES_USER_NOT_FOUND));
        UserPreferences prefs = preferencesRepository.findByUserId(user.getId())
                .orElseThrow(() -> AppException.notFound(ErrorCode.RES_USER_NOT_FOUND));
        return UserResponse.from(user, profile, prefs);
    }

    // Delete account

    @Override
    @Transactional
    public void deleteAccount(DeleteAccountRequest request) {
        User user = securityUtils.currentUser();

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw AppException.unauthorized(ErrorCode.AUTH_PASSWORD_MISMATCH);
        }

        tokenService.revokeAllRefreshTokens(user.getId());
        auditService.log(AuditEvent.ACCOUNT_DELETED, user.getId(), null);

        userRepository.delete(user);
        log.info("Account deleted userId={}", user.getId());
    }

    // Helpers

    private void handleFailedLogin(User user, String clientIp) {
        int attempts = user.getFailedLoginCount() + 1;
        user.setFailedLoginCount(attempts);

        if (attempts >= MAX_FAILED_LOGINS) {
            user.setLockedUntil(Instant.now().plus(LOCK_DURATION_MINUTES, ChronoUnit.MINUTES));
            auditService.log(AuditEvent.ACCOUNT_LOCKED, user.getId(), clientIp);
            log.warn("Account locked userId={} after {} failed attempts", user.getId(), attempts);
        }

        userRepository.save(user);
        auditService.log(AuditEvent.LOGIN_FAILURE, user.getId(), clientIp);
    }

    private void upsertDevice(User user, String deviceId, String deviceName, Platform platform) {
        userDeviceRepository.findByUserIdAndDeviceId(user.getId(), deviceId)
                .ifPresentOrElse(
                        existing -> {
                            existing.setLastSeenAt(Instant.now());
                            if (deviceName != null) existing.setDeviceName(deviceName);
                            existing.setPlatform(platform);
                            userDeviceRepository.save(existing);
                        },
                        () -> userDeviceRepository.save(UserDevice.builder()
                                .user(user)
                                .deviceId(deviceId)
                                .deviceName(deviceName)
                                .platform(platform)
                                .lastSeenAt(Instant.now())
                                .build())
                );
    }

    private Platform parsePlatform(String raw) {
        if (raw == null || raw.isBlank()) return Platform.WEB;
        try {
            return Platform.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Platform.WEB;
        }
    }

    private String extractClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        return xf != null ? xf.split(",")[0].trim() : request.getRemoteAddr();
    }

    private String generateOtp() {
        SecureRandom random = new SecureRandom();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }
}