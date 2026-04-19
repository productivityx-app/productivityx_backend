package com.oussama_chatri.productivityx.core.audit;

public enum AuditEvent {
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    ACCOUNT_LOCKED,
    EMAIL_VERIFIED,
    PASSWORD_RESET,
    PASSWORD_CHANGED,
    TOKEN_REUSE_DETECTED,
    ACCOUNT_DELETED,
    REGISTER
}