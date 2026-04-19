package com.oussama_chatri.productivityx.features.auth.service;

import com.oussama_chatri.productivityx.core.audit.AuditEvent;
import com.oussama_chatri.productivityx.core.audit.AuditService;
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
import com.oussama_chatri.productivityx.features.auth.dto.response.UserResponse;
import com.oussama_chatri.productivityx.features.auth.entity.EmailVerificationToken;
import com.oussama_chatri.productivityx.features.auth.entity.PasswordResetToken;
import com.oussama_chatri.productivityx.features.auth.repository.EmailVerificationTokenRepository;
import com.oussama_chatri.productivityx.features.auth.repository.PasswordResetTokenRepository;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private static final int  MAX_FAILED_LOGINS     = 5;
    private static final long LOCK_DURATION_MINUTES  = 15L;
    private static final int  OTP_MAX_ATTEMPTS       = 5;

    private final UserRepository                   userRepository;
    private final ProfileRepository                profileRepository;
    private final UserPreferencesRepository        preferencesRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository     passwordResetTokenRepository;
    private final PasswordEncoder                  passwordEncoder;
    private final EmailService                     emailService;
    private final SecurityUtils                    securityUtils;
    private final ValidationUtils                  validationUtils;
    private final RateLimiterService               rateLimiterService;
    private final TokenService                     tokenService;
    private final AuditService                     auditService;
    private final CryptoUtils                      cryptoUtils;
    private final StringUtils                      stringUtils;

    @Value("${app.jwt.access-expiry-ms:900000}")
    private long accessExpiryMs;

    @Value("${app.base-url}")
    private String baseUrl;

    // Register

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
        // createEmailVerificationToken returns the token carrying the raw value used for the link
        EmailVerificationToken evToken = tokenService.createEmailVerificationToken(user, otp);

        // The verification URL uses the raw token stored inside the returned entity wrapper.
        // TokenService generates the raw value, hashes it for DB storage, and hands back
        // the raw value via a transient field so only this call site ever sees it.
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

        if (user.isEmailVerified()) {
            throw AppException.badRequest(ErrorCode.AUTH_TOKEN_INVALID);
        }

        // Redis sliding-window rate limit — blocks brute force of all 1,000,000 combinations
        rateLimiterService.checkOtpLimit(user.getId().toString());

        EmailVerificationToken tokenEntity = emailVerificationTokenRepository
                .findLatestActiveOtpForUser(user.getId(), request.getOtp())
                .orElseThrow(() -> {
                    // Increment DB attempt counter even on wrong OTP (defense-in-depth)
                    emailVerificationTokenRepository
                            .findLatestActiveTokenForUser(user.getId())
                            .ifPresent(t -> {
                                t.setOtpAttempts(t.getOtpAttempts() + 1);
                                if (t.getOtpAttempts() >= OTP_MAX_ATTEMPTS) {
                                    t.setUsedAt(Instant.now());
                                }
                                emailVerificationTokenRepository.save(t);
                            });
                    return AppException.unauthorized(ErrorCode.AUTH_TOKEN_INVALID);
                });

        if (Instant.now().isAfter(tokenEntity.getExpiresAt())) {
            throw AppException.unauthorized(ErrorCode.AUTH_TOKEN_EXPIRED);
        }

        // Increment attempt counter and burn the token if the threshold is reached
        int attempts = tokenEntity.getOtpAttempts() + 1;
        tokenEntity.setOtpAttempts(attempts);
        if (attempts >= OTP_MAX_ATTEMPTS) {
            tokenEntity.setUsedAt(Instant.now());
            emailVerificationTokenRepository.save(tokenEntity);
            throw AppException.rateLimited(ErrorCode.RATE_OTP_EXCEEDED);
        }
        emailVerificationTokenRepository.save(tokenEntity);

        return completeVerification(tokenEntity, response);
    }

    // Resend verification

    @Override
    @Transactional
    public void resendVerification(ResendVerificationRequest request) {
        userRepository.findByEmail(request.getEmail().toLowerCase().trim()).ifPresent(user -> {
            if (user.isEmailVerified()) return;
            rateLimiterService.checkResendLimit(user.getId().toString());

            String otp = generateOtp();
            EmailVerificationToken evToken = tokenService.createEmailVerificationToken(user, otp);

            Profile profile  = profileRepository.findByUserId(user.getId()).orElse(null);
            String firstName = profile != null ? profile.getFirstName() : "there";
            String verifyUrl = baseUrl + "/api/v1/auth/verify-email?token=" + evToken.getRawToken();
            emailService.sendVerificationEmail(user.getEmail(), firstName, verifyUrl, otp);
        });
    }

    // Login

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest,
                              HttpServletResponse response) {
        String ip = resolveClientIp(httpRequest);
        rateLimiterService.checkLoginLimit(ip);

        User user = userRepository.findByIdentifier(request.getIdentifier())
                .orElseThrow(() -> AppException.unauthorized(ErrorCode.AUTH_INVALID_CREDENTIALS));

        if (!user.isActive()) {
            throw AppException.forbidden(ErrorCode.AUTH_ACCOUNT_INACTIVE);
        }
        if (user.getLockedUntil() != null && Instant.now().isBefore(user.getLockedUntil())) {
            auditService.log(AuditEvent.ACCOUNT_LOCKED, user.getId(), ip);
            throw AppException.forbidden(ErrorCode.AUTH_ACCOUNT_LOCKED);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            int failures = user.getFailedLoginCount() + 1;
            user.setFailedLoginCount(failures);
            if (failures >= MAX_FAILED_LOGINS) {
                user.setLockedUntil(Instant.now().plus(LOCK_DURATION_MINUTES, ChronoUnit.MINUTES));
                auditService.log(AuditEvent.ACCOUNT_LOCKED, user.getId(), ip,
                        "Locked after " + failures + " failed attempts");
                log.warn("Account locked: {}", stringUtils.maskEmail(user.getEmail()));
            }
            userRepository.save(user);
            auditService.log(AuditEvent.LOGIN_FAILURE, user.getId(), ip);
            throw AppException.unauthorized(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        if (!user.isEmailVerified()) {
            throw AppException.forbidden(ErrorCode.AUTH_EMAIL_NOT_VERIFIED);
        }

        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        auditService.log(AuditEvent.LOGIN_SUCCESS, user.getId(), ip);
        return tokenService.issueTokenPair(user, ip, httpRequest.getHeader("User-Agent"), response);
    }

    // Refresh

    @Override
    @Transactional
    public AuthResponse refresh(String rawRefreshToken, HttpServletResponse response) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw AppException.unauthorized(ErrorCode.AUTH_TOKEN_INVALID);
        }
        var rotated = tokenService.rotateRefreshToken(rawRefreshToken, response);
        String accessToken = tokenService.createAccessToken(rotated.getUser());
        return AuthResponse.of(accessToken, tokenService.accessExpiryMs());
    }

    // Logout

    @Override
    @Transactional
    public void logout(String rawRefreshToken, HttpServletResponse response) {
        tokenService.revokeRefreshToken(rawRefreshToken);
        tokenService.clearRefreshCookie(response);
    }

    // Forgot password

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.getEmail().toLowerCase().trim()).ifPresent(user -> {
            String rawToken  = tokenService.createPasswordResetToken(user);
            Profile profile  = profileRepository.findByUserId(user.getId()).orElse(null);
            String firstName = profile != null ? profile.getFirstName() : "there";
            String resetUrl  = baseUrl + "/auth/reset-password?token=" + rawToken;
            emailService.sendPasswordResetEmail(user.getEmail(), firstName, resetUrl);
        });
    }

    // Reset password

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!validationUtils.isValidPassword(request.getNewPassword())) {
            throw AppException.badRequest(ErrorCode.VAL_WEAK_PASSWORD);
        }

        String hash = cryptoUtils.sha256Hex(request.getToken());
        PasswordResetToken token = passwordResetTokenRepository
                .findByTokenHashAndUsedAtIsNull(hash)
                .orElseThrow(() -> AppException.unauthorized(ErrorCode.AUTH_TOKEN_INVALID));

        if (Instant.now().isAfter(token.getExpiresAt())) {
            throw AppException.unauthorized(ErrorCode.AUTH_TOKEN_EXPIRED);
        }

        token.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(token);

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Invalidate all active sessions across devices after a password reset
        tokenService.revokeAllRefreshTokens(user.getId());
        auditService.log(AuditEvent.PASSWORD_RESET, user.getId(), null);
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

        // Revoke all sessions so stolen refresh tokens are immediately invalidated
        tokenService.revokeAllRefreshTokens(user.getId());
        auditService.log(AuditEvent.PASSWORD_CHANGED, user.getId(), null);
    }

    // Delete account

    @Override
    @Transactional
    public void deleteAccount(DeleteAccountRequest request) {
        User user = securityUtils.currentUser();

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw AppException.badRequest(ErrorCode.AUTH_PASSWORD_MISMATCH);
        }

        auditService.log(AuditEvent.ACCOUNT_DELETED, user.getId(), null,
                stringUtils.maskEmail(user.getEmail()));
        userRepository.delete(user);
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

    // Internal helpers

    private AuthResponse completeVerification(EmailVerificationToken tokenEntity,
                                              HttpServletResponse response) {
        tokenEntity.setUsedAt(Instant.now());
        emailVerificationTokenRepository.save(tokenEntity);

        User user = tokenEntity.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        Profile profile  = profileRepository.findByUserId(user.getId()).orElse(null);
        String firstName = profile != null ? profile.getFirstName() : "there";
        emailService.sendWelcomeEmail(user.getEmail(), firstName);

        auditService.log(AuditEvent.EMAIL_VERIFIED, user.getId(), null);
        log.info("Email verified: {}", stringUtils.maskEmail(user.getEmail()));

        return tokenService.issueTokenPair(user, null, null, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    private String generateOtp() {
        return String.format("%06d", new SecureRandom().nextInt(1_000_000));
    }
}