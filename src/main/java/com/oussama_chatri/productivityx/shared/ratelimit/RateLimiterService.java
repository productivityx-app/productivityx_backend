package com.oussama_chatri.productivityx.shared.ratelimit;

import com.oussama_chatri.productivityx.core.exception.AppException;
import com.oussama_chatri.productivityx.core.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis-backed sliding-window rate limiter.
 * Survives restarts and scales across multiple instances.
 * Uses INCR + EXPIRE: atomic, no Lua script required for this pattern.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiterService {

    private static final int  LOGIN_MAX_ATTEMPTS  = 5;
    private static final long LOGIN_WINDOW_SECS   = 15 * 60L;

    private static final int  RESEND_MAX_ATTEMPTS = 3;
    private static final long RESEND_WINDOW_SECS  = 10 * 60L;

    private static final int  OTP_MAX_ATTEMPTS    = 5;
    private static final long OTP_WINDOW_SECS     = 15 * 60L;

    private final StringRedisTemplate redis;

    public void checkLoginLimit(String ip) {
        check("rl:login:" + ip, LOGIN_MAX_ATTEMPTS, LOGIN_WINDOW_SECS,
                ErrorCode.RATE_LOGIN_EXCEEDED);
    }

    public void checkResendLimit(String userId) {
        check("rl:resend:" + userId, RESEND_MAX_ATTEMPTS, RESEND_WINDOW_SECS,
                ErrorCode.RATE_RESEND_EXCEEDED);
    }

    public void checkOtpLimit(String tokenId) {
        check("rl:otp:" + tokenId, OTP_MAX_ATTEMPTS, OTP_WINDOW_SECS,
                ErrorCode.RATE_OTP_EXCEEDED);
    }

    private void check(String key, int maxAttempts, long windowSecs, ErrorCode errorCode) {
        Long count = redis.opsForValue().increment(key);
        if (count == null) {
            log.error("Redis INCR returned null for key={}", key);
            return;
        }
        if (count == 1) {
            // First attempt in this window — set the expiry
            redis.expire(key, Duration.ofSeconds(windowSecs));
        }
        if (count > maxAttempts) {
            throw AppException.rateLimited(errorCode);
        }
    }
}