package com.oussama_chatri.productivityx.core.exception;

import com.oussama_chatri.productivityx.core.dto.ApiResponse;
import com.oussama_chatri.productivityx.features.notes.dto.response.NoteResponse;
import com.oussama_chatri.productivityx.features.notes.service.MergeService;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * JPA optimistic lock conflict — two concurrent writes to the same entity.
     * The 409 body includes:
     *   - errorCode: "CONFLICT_VERSION"
     *   - serverEntity: the current server copy (client avoids a separate GET)
     *   - currentVersion: the version number the server is now at
     */
    @ExceptionHandler(VersionConflictException.class)
    public ResponseEntity<ConflictResponse<NoteResponse>> handleVersionConflict(VersionConflictException ex) {
        log.warn("Version conflict on note id={}", ex.getServerEntity().getId());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ConflictResponse.<NoteResponse>builder()
                        .success(false)
                        .errorCode("CONFLICT_VERSION")
                        .message("The note was modified by another session. Refresh and retry.")
                        .serverEntity(ex.getServerEntity())
                        .currentVersion(ex.getServerEntity().getVersion())
                        .build());
    }

    /**
     * Three-way merge produced unresolvable overlapping edits.
     * The 409 body includes:
     *   - errorCode: "CONFLICT_MERGE"
     *   - serverEntity: current server content for the client's merge UI
     *   - conflictRegions: list of overlapping line ranges with both sides' text
     */
    @ExceptionHandler(MergeConflictException.class)
    public ResponseEntity<MergeConflictResponse<NoteResponse>> handleMergeConflict(MergeConflictException ex) {
        log.warn("Merge conflict on note id={} regions={}", ex.getServerEntity().getId(), ex.getConflicts().size());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(MergeConflictResponse.<NoteResponse>builder()
                        .success(false)
                        .errorCode("CONFLICT_MERGE")
                        .message("Overlapping edits detected. Manual merge required.")
                        .serverEntity(ex.getServerEntity())
                        .conflictRegions(ex.getConflicts())
                        .build());
    }

    /**
     * fallback: catches any OptimisticLockingFailureException that bubbles up
     * outside of NoteServiceImpl (e.g. from Task, Event, PomodoroSession updates
     * before those services are individually updated to throw VersionConflictException).
     * Returns a generic 409 without the serverEntity since we don't have the type here.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ConflictResponse<Void>> handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.warn("Optimistic lock failure: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ConflictResponse.<Void>builder()
                        .success(false)
                        .errorCode("CONFLICT_VERSION")
                        .message("The resource was modified by another session. Refresh and retry.")
                        .build());
    }

    private boolean isSseRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    private ResponseEntity<SseEmitter> sseError(HttpStatus status, String errorCode, String message) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            String safeMsg = message == null ? "" : message.replace("\"", "\\\"");
            String payload  = "{\"success\":false,\"errorCode\":\"" + errorCode
                    + "\",\"message\":\"" + safeMsg + "\"}";
            emitter.send(SseEmitter.event().name("error").data(payload));
        } catch (IOException ignored) {
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

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(
                        ErrorCode.VAL_REQUEST_BODY_INVALID.getCode(),
                        "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'."));
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

    // Conflict response DTOs (inner classes — no separate files needed)

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ConflictResponse<T> {
        private final boolean success;
        private final String errorCode;
        private final String message;
        private final T serverEntity;
        private final Integer currentVersion;
    }

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MergeConflictResponse<T> {
        private final boolean success;
        private final String errorCode;
        private final String message;
        private final T serverEntity;
        private final List<MergeService.ConflictRegion> conflictRegions;
    }
}
