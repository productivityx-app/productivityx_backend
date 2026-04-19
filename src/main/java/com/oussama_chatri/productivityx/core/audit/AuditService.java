package com.oussama_chatri.productivityx.core.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Async
    public void log(AuditEvent event, UUID userId, String ipAddress, String detail) {
        try {
            auditLogRepository.save(AuditLog.builder()
                    .event(event)
                    .userId(userId)
                    .ipAddress(ipAddress)
                    .detail(detail)
                    .build());
        } catch (Exception ex) {
            // Audit failure must never break the primary flow
            log.error("Failed to write audit log event={} userId={}: {}", event, userId, ex.getMessage());
        }
    }

    @Async
    public void log(AuditEvent event, UUID userId, String ipAddress) {
        log(event, userId, ipAddress, null);
    }
}