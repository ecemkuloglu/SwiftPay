package com.swiftpay.domain;

/** Values stored in {@code audit_log.event_type}. */
public enum AuditEventType {
    TRANSFER_COMPLETED,
    /** A transfer row was stored as FAILED (business rule violation). */
    TRANSFER_FAILED,
    /** Rejected before a transfer row could be created (source account not owned / missing). */
    TRANSFER_REJECTED,
    /** Wrong password or unknown username (the password itself is never stored). */
    LOGIN_FAILED
}
