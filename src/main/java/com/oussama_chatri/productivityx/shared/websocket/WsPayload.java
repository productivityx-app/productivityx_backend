package com.oussama_chatri.productivityx.shared.websocket;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Wrapper for every WebSocket push message. Carries a server-generated messageId
 * so clients can deduplicate on reconnect. The messageId is also tracked server-side
 * in Redis with a 5-minute TTL — defensive against duplicate sends.
 *
 * @param <T> the inner payload type (NoteResponse, TaskResponse, etc.)
 */
@Getter
@Builder
public class WsPayload<T> {

    /** Server-generated UUID for deduplication. */
    private final String messageId;

    /** Event topic — e.g. "notes.created", "tasks.updated". */
    private final String topic;

    /** Server epoch millis — clients can use to detect stale messages. */
    private final long sentAt;

    /** The actual payload. */
    private final T data;

    public static <T> WsPayload<T> of(String topic, T data) {
        return WsPayload.<T>builder()
                .messageId(UUID.randomUUID().toString())
                .topic(topic)
                .sentAt(Instant.now().toEpochMilli())
                .data(data)
                .build();
    }
}