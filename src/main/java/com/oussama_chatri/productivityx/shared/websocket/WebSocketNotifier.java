package com.oussama_chatri.productivityx.shared.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * WebSocket push gateway. Every outbound message is wrapped in {@link WsPayload}
 * with a unique messageId for client-side deduplication. Redis tracks recently
 * sent IDs server-side (5-min TTL) as a defensive guard against duplicate sends.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketNotifier {

    private static final String WS_DEDUP_PREFIX = "ws:dedup:";
    private static final Duration DEDUP_TTL = Duration.ofMinutes(5);

    private final SimpMessagingTemplate messagingTemplate;
    private final StringRedisTemplate redisTemplate;

    /**
     * Sends a typed payload to the user's personal queue.
     * The payload is wrapped in WsPayload which carries a unique messageId.
     * Clients should maintain a sliding window of the last 100-200 seen IDs
     * and silently drop duplicates.
     */
    public <T> void notifyUser(UUID userId, String topic, T payload) {
        WsPayload<T> wrapped = WsPayload.of(topic, payload);

        // Defensive server-side dedup — skip if this exact ID was already sent
        String dedupKey = WS_DEDUP_PREFIX + wrapped.getMessageId();
        Boolean added = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", DEDUP_TTL);
        if (Boolean.FALSE.equals(added)) {
            log.debug("WebSocket dedup skip messageId={} topic={} userId={}",
                    wrapped.getMessageId(), topic, userId);
            return;
        }

        String destination = "/user/" + userId + "/queue/events";
        messagingTemplate.convertAndSend(destination, wrapped);

        log.debug("WebSocket notify userId={} topic={} messageId={}",
                userId, topic, wrapped.getMessageId());
    }

    /**
     * Broadcasts a payload to all subscribers on a topic.
     * Also wrapped in WsPayload with deduplication support.
     */
    public <T> void broadcast(String topic, T payload) {
        WsPayload<T> wrapped = WsPayload.of(topic, payload);

        String dedupKey = WS_DEDUP_PREFIX + wrapped.getMessageId();
        redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", DEDUP_TTL);

        messagingTemplate.convertAndSend("/topic/" + topic, wrapped);

        log.debug("WebSocket broadcast topic={} messageId={}", topic, wrapped.getMessageId());
    }
}