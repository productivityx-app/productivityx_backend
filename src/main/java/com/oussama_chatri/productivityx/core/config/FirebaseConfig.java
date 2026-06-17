package com.oussama_chatri.productivityx.core.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * Initializes the Firebase Admin SDK for FCM push notifications.
 * Expects GOOGLE_APPLICATION_CREDENTIALS env var pointing to a service account JSON.
 * Falls back gracefully if the credential file is absent (FCM features are disabled).
 */
@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${app.firebase.enabled:false}")
    private boolean enabled;

    @Bean
    public FirebaseApp firebaseApp() {
        if (!enabled) {
            log.info("Firebase Admin SDK is disabled (app.firebase.enabled=false)");
            return null;
        }

        String credPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (credPath == null || credPath.isBlank()) {
            log.warn("GOOGLE_APPLICATION_CREDENTIALS not set — FCM push notifications are disabled");
            return null;
        }

        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        try (FileInputStream serviceAccount = new FileInputStream(credPath)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();
            FirebaseApp app = FirebaseApp.initializeApp(options);
            log.info("Firebase Admin SDK initialized successfully");
            return app;
        } catch (IOException ex) {
            log.error("Failed to initialize Firebase Admin SDK: {}", ex.getMessage());
            return null;
        }
    }
}