package com.oussama_chatri.productivityx.shared.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Thin wrapper around SimpMessagingTemplate.
 * <p>
 * Features inject this component when they need to push real-time events to a specific user.
 * Client subscribes to: {@code /user/{userId}/queue/{topic}}
 * <p>
 * Topics used in this project:
 * <ul>
 *   <li>notes.created / notes.updated / notes.deleted / notes.restored</li>
 *   <li>tasks.created / tasks.updated / tasks.deleted / tasks.restored</li>
 *   <li>events.created / events.updated / events.deleted / events.restored</li>
 *   <li>pomodoro.started / pomodoro.completed / pomodoro.interrupted</li>
 *   <li>sync.delta</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketNotifier {

    private final SimpMessagingTemplate messagingTemplate;

    public void notifyUser(UUID userId, String topic, Object payload) {
        String destination = "/queue/" + topic;
        try {
            messagingTemplate.convertAndSendToUser(userId.toString(), destination, payload);
            log.debug("WS push → user={} topic={}", userId, topic);
        } catch (Exception ex) {
            log.error("WS push failed → user={} topic={}: {}", userId, topic, ex.getMessage());
        }
    }

    public void broadcast(String topic, Object payload) {
        messagingTemplate.convertAndSend("/topic/" + topic, payload);
    }
}
