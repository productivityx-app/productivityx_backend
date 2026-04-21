package com.oussama_chatri.productivityx.shared.email;

import com.oussama_chatri.productivityx.core.config.BrevoConfig;
import com.oussama_chatri.productivityx.core.exception.AppException;
import com.oussama_chatri.productivityx.core.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@Slf4j
public class EmailService {

    private final RestTemplate brevoRestTemplate;
    private final BrevoConfig  brevoConfig;

    @Value("${app.brevo.sender-email}")
    private String senderEmail;

    @Value("${app.brevo.sender-name:ProductivityX}")
    private String senderName;

    public EmailService(@Qualifier("brevoRestTemplate") RestTemplate brevoRestTemplate,
                        BrevoConfig brevoConfig) {
        this.brevoRestTemplate = brevoRestTemplate;
        this.brevoConfig = brevoConfig;
    }

    @Async
    public void sendVerificationEmail(String toEmail, String firstName,
                                      String verificationUrl, String otp) {
        String html = EmailTemplates.verificationEmail(firstName, verificationUrl, otp);
        send(toEmail, firstName, "Verify your ProductivityX account", html);
    }

    /**
     * Sends the password reset email with both an OTP (for the mobile app) and
     * a magic link (fallback for web). Both the link token and OTP are valid for 1 hour.
     */
    @Async
    public void sendPasswordResetEmail(String toEmail, String firstName,
                                       String resetUrl, String otp) {
        String html = EmailTemplates.passwordResetEmail(firstName, resetUrl, otp);
        send(toEmail, firstName, "Reset your ProductivityX password", html);
    }

    @Async
    public void sendWelcomeEmail(String toEmail, String firstName) {
        String html = EmailTemplates.welcomeEmail(firstName);
        send(toEmail, firstName, "Welcome to ProductivityX 🎉", html);
    }

    @Retryable(
            retryFor  = Exception.class,
            maxAttempts = 3,
            backoff   = @Backoff(delay = 1000, multiplier = 2)
    )
    void send(String toEmail, String toName, String subject, String htmlContent) {
        Map<String, Object> payload = Map.of(
                "sender",      Map.of("name", senderName, "email", senderEmail),
                "to",          new Object[]{ Map.of("email", toEmail, "name", toName) },
                "subject",     subject,
                "htmlContent", htmlContent
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        ResponseEntity<String> response = brevoRestTemplate.postForEntity(
                brevoConfig.getBaseUrl() + "/smtp/email", request, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("Brevo returned non-2xx for {} — status: {}", toEmail, response.getStatusCode());
            throw AppException.internal(ErrorCode.EXT_EMAIL_SEND_FAILED);
        }
        log.debug("Email sent to {}: {}", toEmail, subject);
    }

    @Recover
    void recoverSend(Exception ex, String toEmail, String toName, String subject, String htmlContent) {
        log.error("Email delivery permanently failed after retries — to={} subject={}: {}",
                toEmail, subject, ex.getMessage());
    }
}