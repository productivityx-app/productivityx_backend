package com.oussama_chatri.productivityx.shared.push;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Firebase Cloud Messaging gateway. Sends push notifications to offline devices
 * when important events (task reminders, shared notes, Pomodoro end) occur and
 * the user has no active WebSocket session.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FcmService {

    /**
     * Sends a push notification to a single device.
     *
     * @param pushToken FCM registration token from the device
     * @param title     notification title (localized by caller)
     * @param body      notification body
     * @param data      key-value payload forwarded to the client app
     */
    public void sendPush(String pushToken, String title, String body, Map<String, String> data) {
        if (pushToken == null || pushToken.isBlank()) {
            log.debug("FCM push skipped — no push token");
            return;
        }

        Message message = Message.builder()
                .setToken(pushToken)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putAllData(data != null ? data : Map.of())
                .build();

        try {
            String messageId = FirebaseMessaging.getInstance().send(message);
            log.debug("FCM sent messageId={} tokenPrefix={}", messageId, pushToken.substring(0, 8));
        } catch (FirebaseMessagingException ex) {
            // Invalid token — client will re-register on next app launch
            if (ex.getMessagingErrorCode() != null
                    && ex.getMessagingErrorCode().name().contains("UNREGISTERED")) {
                log.warn("FCM token unregistered, pushTokenPrefix={}", pushToken.substring(0, 8));
            } else {
                log.error("FCM send failed: {}", ex.getMessage());
            }
        }
    }

    /**
     * Sends a push to multiple devices (same user, different devices).
     * Silently skips invalid tokens.
     */
    public void sendPushToTokens(java.util.List<String> pushTokens, String title, String body,
                                 Map<String, String> data) {
        for (String token : pushTokens) {
            try {
                sendPush(token, title, body, data);
            } catch (Exception ex) {
                log.debug("FCM multi-send skip for one token: {}", ex.getMessage());
            }
        }
    }
}