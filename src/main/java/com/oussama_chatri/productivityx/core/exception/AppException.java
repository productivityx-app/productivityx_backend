package com.oussama_chatri.productivityx.core.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus status;

    public AppException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.status = errorCode.getStatus();
    }

    public AppException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
        this.status = errorCode.getStatus();
    }

    public static AppException notFound(ErrorCode code)                      { return new AppException(code); }
    public static AppException forbidden()                                    { return new AppException(ErrorCode.GEN_FORBIDDEN); }
    public static AppException forbidden(ErrorCode code)                     { return new AppException(code); }
    public static AppException conflict(ErrorCode code)                      { return new AppException(code); }
    public static AppException badRequest(ErrorCode code)                    { return new AppException(code); }
    public static AppException badRequest(ErrorCode code, String msg)        { return new AppException(code, msg); }
    public static AppException unauthorized(ErrorCode code)                  { return new AppException(code); }
    public static AppException rateLimited(ErrorCode code)                   { return new AppException(code); }
    public static AppException internal(ErrorCode code)                      { return new AppException(code); }
}
