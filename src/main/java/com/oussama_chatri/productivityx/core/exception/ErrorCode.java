package com.oussama_chatri.productivityx.core.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // Auth
    AUTH_INVALID_CREDENTIALS("AUTH_001", "Invalid email or password.", HttpStatus.UNAUTHORIZED),
    AUTH_EMAIL_NOT_VERIFIED("AUTH_002", "Email address has not been verified.", HttpStatus.FORBIDDEN),
    AUTH_ACCOUNT_LOCKED("AUTH_003", "Account is temporarily locked. Try again later.", HttpStatus.FORBIDDEN),
    AUTH_ACCOUNT_INACTIVE("AUTH_004", "Account is deactivated.", HttpStatus.FORBIDDEN),
    AUTH_TOKEN_EXPIRED("AUTH_005", "Token has expired.", HttpStatus.UNAUTHORIZED),
    AUTH_TOKEN_INVALID("AUTH_006", "Token is invalid or already used.", HttpStatus.UNAUTHORIZED),
    AUTH_TOKEN_NOT_FOUND("AUTH_007", "Token not found.", HttpStatus.NOT_FOUND),
    AUTH_REFRESH_TOKEN_REVOKED("AUTH_008", "Refresh token has been revoked.", HttpStatus.UNAUTHORIZED),
    AUTH_PASSWORD_MISMATCH("AUTH_009", "Current password is incorrect.", HttpStatus.BAD_REQUEST),
    AUTH_DUPLICATE_EMAIL("AUTH_010", "An account with this email already exists.", HttpStatus.CONFLICT),
    AUTH_DUPLICATE_USERNAME("AUTH_011", "This username is already taken.", HttpStatus.CONFLICT),
    AUTH_DUPLICATE_PHONE("AUTH_012", "An account with this phone number already exists.", HttpStatus.CONFLICT),
    AUTH_UNDERAGE("AUTH_013", "You must meet the minimum age requirement to register.", HttpStatus.BAD_REQUEST),

    // Resource
    RES_NOT_FOUND("RES_001", "Resource not found.", HttpStatus.NOT_FOUND),
    RES_USER_NOT_FOUND("RES_002", "User not found.", HttpStatus.NOT_FOUND),
    RES_NOTE_NOT_FOUND("RES_003", "Note not found.", HttpStatus.NOT_FOUND),
    RES_TASK_NOT_FOUND("RES_004", "Task not found.", HttpStatus.NOT_FOUND),
    RES_EVENT_NOT_FOUND("RES_005", "Event not found.", HttpStatus.NOT_FOUND),
    RES_TAG_NOT_FOUND("RES_006", "Tag not found.", HttpStatus.NOT_FOUND),
    RES_CONVERSATION_NOT_FOUND("RES_007", "Conversation not found.", HttpStatus.NOT_FOUND),
    RES_POMODORO_SESSION_NOT_FOUND("RES_008", "Pomodoro session not found.", HttpStatus.NOT_FOUND),

    // Validation
    VAL_INVALID_EMAIL("VAL_001", "Invalid email address format.", HttpStatus.BAD_REQUEST),
    VAL_WEAK_PASSWORD("VAL_002", "Password does not meet strength requirements.", HttpStatus.BAD_REQUEST),
    VAL_INVALID_PHONE("VAL_003", "Invalid phone number format.", HttpStatus.BAD_REQUEST),
    VAL_INVALID_USERNAME("VAL_004", "Username must be 3–30 characters, alphanumeric and underscores only.", HttpStatus.BAD_REQUEST),
    VAL_REQUEST_BODY_INVALID("VAL_005", "Request body contains invalid fields.", HttpStatus.BAD_REQUEST),
    VAL_CONSTRAINT_VIOLATION("VAL_006", "One or more constraint violations.", HttpStatus.BAD_REQUEST),

    // Rate limiting
    RATE_LOGIN_EXCEEDED("RATE_001", "Too many login attempts. Please wait before trying again.", HttpStatus.TOO_MANY_REQUESTS),
    RATE_RESEND_EXCEEDED("RATE_002", "Too many resend requests. Please wait before trying again.", HttpStatus.TOO_MANY_REQUESTS),

    // External services
    EXT_EMAIL_SEND_FAILED("EXT_001", "Failed to send email. Please try again later.", HttpStatus.INTERNAL_SERVER_ERROR),
    EXT_AI_UNAVAILABLE("EXT_002", "AI service is currently unavailable.", HttpStatus.SERVICE_UNAVAILABLE),
    EXT_AI_STREAM_ERROR("EXT_003", "An error occurred while streaming the AI response.", HttpStatus.INTERNAL_SERVER_ERROR),

    // General
    GEN_FORBIDDEN("GEN_001", "You do not have permission to access this resource.", HttpStatus.FORBIDDEN),
    GEN_INTERNAL_ERROR("GEN_002", "An unexpected error occurred. Please try again.", HttpStatus.INTERNAL_SERVER_ERROR),
    GEN_METHOD_NOT_ALLOWED("GEN_003", "HTTP method not allowed.", HttpStatus.METHOD_NOT_ALLOWED),
    GEN_MEDIA_TYPE_UNSUPPORTED("GEN_004", "Unsupported media type.", HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    // Rate limiting
    RATE_OTP_EXCEEDED("RATE_003", "Too many failed attempts. This code is now invalid.", HttpStatus.TOO_MANY_REQUESTS);

    // External services

    private final String code;
    private final String message;
    private final HttpStatus status;

    ErrorCode(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }
}
