package com.oussama_chatri.productivityx.core.exception;

import com.oussama_chatri.productivityx.core.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Returns true when the client declared it only accepts text/event-stream.
    // SSE endpoints set produces=TEXT_EVENT_STREAM_VALUE, so any exception thrown before
    // or during streaming must be sent back as an SSE event — not JSON — otherwise Spring
    // throws HttpMediaTypeNotAcceptableException on top of the original error.
    private boolean isSseRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    // Builds a minimal SSE error event without an ObjectMapper dependency.
    // The payload is a JSON object matching the standard ApiResponse error shape so
    // the Android client can parse it with its existing error-handling logic.
    private ResponseEntity<SseEmitter> sseError(HttpStatus status, String errorCode, String message) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            String safeMsg = message == null ? "" : message.replace("\"", "\\\"");
            String payload  = "{\"success\":false,\"errorCode\":\"" + errorCode
                    + "\",\"message\":\"" + safeMsg + "\"}";
            emitter.send(SseEmitter.event().name("error").data(payload));
        } catch (IOException ignored) {
            // Client already disconnected
        } finally {
            emitter.complete();
        }
        return ResponseEntity.status(status)
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emitter);
    }

    @ExceptionHandler(AppException.class)
    public Object handleAppException(AppException ex, HttpServletRequest request) {
        log.warn("AppException [{}]: {}", ex.getErrorCode().getCode(), ex.getMessage());
        if (isSseRequest(request)) {
            return sseError(ex.getStatus(), ex.getErrorCode().getCode(), ex.getMessage());
        }
        return ResponseEntity.status(ex.getStatus())
                .body(ApiResponse.error(ex.getErrorCode().getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.VAL_REQUEST_BODY_INVALID.getCode(), message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.VAL_REQUEST_BODY_INVALID.getCode(), message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(
                        ErrorCode.VAL_REQUEST_BODY_INVALID.getCode(),
                        "Malformed or missing request body."));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(
                        ErrorCode.VAL_REQUEST_BODY_INVALID.getCode(),
                        "Required parameter '" + ex.getParameterName() + "' is missing."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(
                        ErrorCode.GEN_FORBIDDEN.getCode(),
                        ErrorCode.GEN_FORBIDDEN.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(
                        ErrorCode.AUTH_WRONG_PASSWORD.getCode(),
                        ErrorCode.AUTH_WRONG_PASSWORD.getMessage()));
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ApiResponse<Void>> handleLocked(LockedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(
                        ErrorCode.AUTH_ACCOUNT_LOCKED.getCode(),
                        ErrorCode.AUTH_ACCOUNT_LOCKED.getMessage()));
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiResponse<Void>> handleDisabled(DisabledException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(
                        ErrorCode.AUTH_ACCOUNT_INACTIVE.getCode(),
                        ErrorCode.AUTH_ACCOUNT_INACTIVE.getMessage()));
    }

    // Never pass ex.getMessage() to the client — it can expose Spring internal state.
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        log.debug("AuthenticationException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(
                        ErrorCode.AUTH_UNAUTHENTICATED.getCode(),
                        ErrorCode.AUTH_UNAUTHENTICATED.getMessage()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error(
                        ErrorCode.GEN_METHOD_NOT_ALLOWED.getCode(),
                        ErrorCode.GEN_METHOD_NOT_ALLOWED.getMessage()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.error(
                        ErrorCode.GEN_MEDIA_TYPE_UNSUPPORTED.getCode(),
                        ErrorCode.GEN_MEDIA_TYPE_UNSUPPORTED.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public Object handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        if (isSseRequest(request)) {
            return sseError(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ErrorCode.GEN_INTERNAL_ERROR.getCode(),
                    ErrorCode.GEN_INTERNAL_ERROR.getMessage());
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(
                        ErrorCode.GEN_INTERNAL_ERROR.getCode(),
                        ErrorCode.GEN_INTERNAL_ERROR.getMessage()));
    }
}