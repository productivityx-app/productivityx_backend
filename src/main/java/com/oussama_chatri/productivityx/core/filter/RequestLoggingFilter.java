package com.oussama_chatri.productivityx.core.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.UUID;

/**
 * Request logging filter ? generates a unique request ID for every request
 * and logs method, path, status, and duration. The request ID is stored in
 * MDC and returned as {@code X-Request-ID} so clients can correlate logs.
 *
 * <p>Runs early in the filter chain (highest precedence after security)
 * so that the request ID is available to all downstream components.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter implements Filter {

    private static final String HEADER_NAME = "X-Request-ID";
    private static final int CONTENT_CACHE_LIMIT = 1024; // 1KB cache limit

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest httpReq)
                || !(response instanceof HttpServletResponse httpResp)) {
            chain.doFilter(request, response);
            return;
        }

        String requestId = resolveRequestId(httpReq);
        httpResp.setHeader(HEADER_NAME, requestId);

        long start = System.currentTimeMillis();

        ContentCachingRequestWrapper wrappedReq =
                new ContentCachingRequestWrapper(httpReq, CONTENT_CACHE_LIMIT);
        ContentCachingResponseWrapper wrappedResp =
                new ContentCachingResponseWrapper(httpResp);

        try {
            chain.doFilter(wrappedReq, wrappedResp);
        } finally {
            long duration = System.currentTimeMillis() - start;
            log.info("{} {} {} ? {} ms (request-id: {})",
                    httpReq.getMethod(),
                    httpReq.getRequestURI(),
                    wrappedResp.getStatus(),
                    duration,
                    requestId);
            wrappedResp.copyBodyToResponse();
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String header = request.getHeader(HEADER_NAME);
        return (header != null && !header.isBlank()) ? header : UUID.randomUUID().toString();
    }
}