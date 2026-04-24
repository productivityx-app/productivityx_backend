package com.oussama_chatri.productivityx.core.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // Auth — credentials & identity
    AUTH_USER_NOT_FOUND("AUTH_001", "No account found with that email, username, or phone.", HttpStatus.UNAUTHORIZED),
    AUTH_WRONG_PASSWORD("AUTH_002", "The password you entered is incorrect.", HttpStatus.UNAUTHORIZED),
    AUTH_EMAIL_NOT_VERIFIED("AUTH_003", "Your email address has not been verified yet. Check your inbox for the verification code.", HttpStatus.FORBIDDEN),
    AUTH_ACCOUNT_LOCKED("AUTH_004", "Your account has been temporarily locked after too many failed attempts. Try again in 15 minutes.", HttpStatus.FORBIDDEN),
    AUTH_ACCOUNT_INACTIVE("AUTH_005", "This account has been deactivated. Contact support if you believe this is a mistake.", HttpStatus.FORBIDDEN),
    AUTH_EMAIL_ALREADY_VERIFIED("AUTH_006", "This email address is already verified. You can log in.", HttpStatus.BAD_REQUEST),

    // Auth — tokens
    AUTH_TOKEN_EXPIRED("AUTH_007", "This token has expired. Please request a new one.", HttpStatus.UNAUTHORIZED),
    AUTH_TOKEN_INVALID("AUTH_008", "This token is invalid or has already been used.", HttpStatus.UNAUTHORIZED),
    AUTH_TOKEN_NOT_FOUND("AUTH_009", "Token not found.", HttpStatus.NOT_FOUND),
    AUTH_REFRESH_TOKEN_REVOKED("AUTH_010", "Your session has been invalidated. Please log in again.", HttpStatus.UNAUTHORIZED),
    AUTH_UNAUTHENTICATED("AUTH_011", "Authentication is required to access this resource.", HttpStatus.UNAUTHORIZED),

    // Auth — account management
    AUTH_PASSWORD_MISMATCH("AUTH_012", "The current password you entered is incorrect.", HttpStatus.BAD_REQUEST),
    AUTH_DUPLICATE_EMAIL("AUTH_013", "An account with this email address already exists.", HttpStatus.CONFLICT),
    AUTH_DUPLICATE_USERNAME("AUTH_014", "This username is already taken. Please choose a different one.", HttpStatus.CONFLICT),
    AUTH_DUPLICATE_PHONE("AUTH_015", "An account with this phone number already exists.", HttpStatus.CONFLICT),
    AUTH_UNDERAGE("AUTH_016", "You must be at least 13 years old to create an account.", HttpStatus.BAD_REQUEST),

    // Resources
    RES_NOT_FOUND("RES_001", "The requested resource was not found.", HttpStatus.NOT_FOUND),
    RES_USER_NOT_FOUND("RES_002", "User not found.", HttpStatus.NOT_FOUND),
    RES_NOTE_NOT_FOUND("RES_003", "Note not found or you do not have access to it.", HttpStatus.NOT_FOUND),
    RES_TASK_NOT_FOUND("RES_004", "Task not found or you do not have access to it.", HttpStatus.NOT_FOUND),
    RES_EVENT_NOT_FOUND("RES_005", "Event not found or you do not have access to it.", HttpStatus.NOT_FOUND),
    RES_TAG_NOT_FOUND("RES_006", "Tag not found or you do not have access to it.", HttpStatus.NOT_FOUND),
    RES_CONVERSATION_NOT_FOUND("RES_007", "Conversation not found or you do not have access to it.", HttpStatus.NOT_FOUND),
    RES_POMODORO_SESSION_NOT_FOUND("RES_008", "Pomodoro session not found or you do not have access to it.", HttpStatus.NOT_FOUND),

    // Validation
    VAL_INVALID_EMAIL("VAL_001", "Please enter a valid email address (e.g. name@example.com).", HttpStatus.BAD_REQUEST),
    VAL_WEAK_PASSWORD("VAL_002", "Password must be at least 8 characters and include an uppercase letter, a lowercase letter, a number, and a special character (@$!%*?&_#^).", HttpStatus.BAD_REQUEST),
    VAL_INVALID_PHONE("VAL_003", "Please enter a valid international phone number (e.g. +213555000001).", HttpStatus.BAD_REQUEST),
    VAL_INVALID_USERNAME("VAL_004", "Username must be 3–30 characters and contain only letters, numbers, and underscores.", HttpStatus.BAD_REQUEST),
    VAL_REQUEST_BODY_INVALID("VAL_005", "The request contains invalid or missing fields.", HttpStatus.BAD_REQUEST),
    VAL_DUPLICATE_TAG_NAME("VAL_006", "A tag with this name already exists. Tag names are unique per user.", HttpStatus.CONFLICT),
    VAL_SUBTASK_DEPTH_EXCEEDED("VAL_007", "Subtasks cannot be nested more than one level deep.", HttpStatus.BAD_REQUEST),
    VAL_EVENT_TIME_RANGE("VAL_008", "Event end time must be after the start time.", HttpStatus.BAD_REQUEST),
    VAL_SESSION_ALREADY_ACTIVE("VAL_009", "You already have an active Pomodoro session. End it before starting a new one.", HttpStatus.BAD_REQUEST),
    VAL_SESSION_ALREADY_ENDED("VAL_010", "This Pomodoro session has already ended.", HttpStatus.BAD_REQUEST),
    VAL_NOTE_TRASHED("VAL_011", "This note is in the trash. Restore it before editing.", HttpStatus.BAD_REQUEST),
    VAL_NOTE_NOT_IN_TRASH("VAL_012", "This note is not in the trash and cannot be restored.", HttpStatus.BAD_REQUEST),
    VAL_TASK_NOT_IN_TRASH("VAL_013", "This task is not in the trash and cannot be restored.", HttpStatus.BAD_REQUEST),
    VAL_TASK_MUST_BE_TRASHED_FIRST("VAL_014", "Move the task to trash before permanently deleting it.", HttpStatus.BAD_REQUEST),
    VAL_NOTE_MUST_BE_TRASHED_FIRST("VAL_015", "Move the note to trash before permanently deleting it.", HttpStatus.BAD_REQUEST),
    VAL_SYNC_RANGE_TOO_LARGE("VAL_016", "Sync range too large. The 'since' parameter cannot be more than 30 days in the past.", HttpStatus.BAD_REQUEST),

    // Rate limiting
    RATE_LOGIN_EXCEEDED("RATE_001", "Too many login attempts from this IP. Please wait 15 minutes before trying again.", HttpStatus.TOO_MANY_REQUESTS),
    RATE_RESEND_EXCEEDED("RATE_002", "You have requested too many verification emails. Please wait 10 minutes before trying again.", HttpStatus.TOO_MANY_REQUESTS),
    RATE_OTP_EXCEEDED("RATE_003", "Too many incorrect code attempts. This code is now invalid. Request a new one.", HttpStatus.TOO_MANY_REQUESTS),

    // External services
    EXT_EMAIL_SEND_FAILED("EXT_001", "We could not send the email right now. Please try again in a few minutes.", HttpStatus.INTERNAL_SERVER_ERROR),
    EXT_AI_UNAVAILABLE("EXT_002", "The AI service is currently unavailable. Please try again later.", HttpStatus.SERVICE_UNAVAILABLE),
    EXT_AI_STREAM_ERROR("EXT_003", "An error occurred while streaming the AI response. Please try again.", HttpStatus.INTERNAL_SERVER_ERROR),

    // General
    GEN_FORBIDDEN("GEN_001", "You do not have permission to perform this action.", HttpStatus.FORBIDDEN),
    GEN_INTERNAL_ERROR("GEN_002", "An unexpected error occurred on our end. Please try again.", HttpStatus.INTERNAL_SERVER_ERROR),
    GEN_METHOD_NOT_ALLOWED("GEN_003", "This HTTP method is not supported for this endpoint.", HttpStatus.METHOD_NOT_ALLOWED),
    GEN_MEDIA_TYPE_UNSUPPORTED("GEN_004", "Unsupported content type. Use application/json.", HttpStatus.UNSUPPORTED_MEDIA_TYPE);

    private final String code;
    private final String message;
    private final HttpStatus status;

    ErrorCode(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }
}
