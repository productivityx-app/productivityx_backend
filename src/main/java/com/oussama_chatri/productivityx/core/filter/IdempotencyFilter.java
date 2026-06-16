package com.oussama_chatri.productivityx.core.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * Idempotency filter — prevents duplicate operations on network retries.
 *
 * <p>How it works:
 * <ol>
 *   <li>Client generates a random UUID and sends it as {@code Idempotency-Key: <uuid>}.</li>
 *   <li>This filter checks Redis for {@code idempotency:{userId}:{key}}.</li>
 *   <li>Cache hit → replay the cached (statusCode, body) immediately. The controller
 *       never executes, so no duplicate note/task/event is created.</li>
 *   <li>Cache miss → execute the chain, then store (status, body) in Redis with a
 *       24-hour TTL before returning.</li>
 * </ol>
 *
 * <p>Only mutating methods (POST, PUT, PATCH, DELETE) are intercepted.
 * GET is pass-through. 5xx responses are not cached (transient server errors
 * should be retried and may succeed next time).
 *
 * <p>The filter runs <em>after</em> {@code JwtAuthFilter} so that
 * {@code SecurityContextHolder} is populated and we can use the userId as
 * part of the cache key (preventing cross-user key collisions).
 */
@Slf4j
@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME       = "Idempotency-Key";
    private static final String KEY_PREFIX         = "idempotency:";
    private static final Duration TTL              = Duration.ofHours(24);
    private static final String FIELD_STATUS       = "status";
    private static final String FIELD_BODY         = "body";
    private static final String FIELD_CONTENT_TYPE = "content_type";

    private static final Set<String> MUTABLE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper        objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String idempotencyKey = request.getHeader(HEADER_NAME);
        if (idempotencyKey == null || !MUTABLE_METHODS.contains(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String userId = resolveUserId();
        if (userId == null) {
            // Not authenticated yet — let the security filter handle it
            chain.doFilter(request, response);
            return;
        }

        String redisKey = KEY_PREFIX + userId + ":" + idempotencyKey;

        // Cache hit — replay the stored response
        String cachedStatus = redisTemplate.opsForHash().get(redisKey, FIELD_STATUS) != null
                ? (String) redisTemplate.opsForHash().get(redisKey, FIELD_STATUS)
                : null;

        if (cachedStatus != null) {
            String cachedBody        = (String) redisTemplate.opsForHash().get(redisKey, FIELD_BODY);
            String cachedContentType = (String) redisTemplate.opsForHash().get(redisKey, FIELD_CONTENT_TYPE);

            log.debug("Idempotency cache hit key={} userId={} status={}", idempotencyKey, userId, cachedStatus);

            response.setStatus(Integer.parseInt(cachedStatus));
            response.setContentType(cachedContentType != null ? cachedContentType : "application/json");
            if (cachedBody != null) {
                response.getWriter().write(cachedBody);
            }
            return;
        }

        // Cache miss — execute the request and capture the response
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        chain.doFilter(request, wrappedResponse);

        int statusCode = wrappedResponse.getStatus();

        // Do not cache server errors — they should be retried and may succeed
        if (statusCode < 500) {
            byte[] responseBody = wrappedResponse.getContentAsByteArray();
            String bodyStr = responseBody.length > 0 ? new String(responseBody) : "";

            redisTemplate.opsForHash().put(redisKey, FIELD_STATUS, String.valueOf(statusCode));
            redisTemplate.opsForHash().put(redisKey, FIELD_BODY, bodyStr);
            redisTemplate.opsForHash().put(redisKey, FIELD_CONTENT_TYPE,
                    wrappedResponse.getContentType() != null ? wrappedResponse.getContentType() : "application/json");
            redisTemplate.expire(redisKey, TTL);

            log.debug("Idempotency cached key={} userId={} status={}", idempotencyKey, userId, statusCode);
        }

        // ContentCachingResponseWrapper buffers the body — must copy it to the real response
        wrappedResponse.copyBodyToResponse();
    }

    private String resolveUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) return null;
            return auth.getName();
        } catch (Exception ex) {
            return null;
        }
    }
}
