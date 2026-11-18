package com.swiftpay.domain;

/** Allowed transitions: PENDING -> COMPLETED, PENDING -> FAILED. */
public enum TransferStatus {
    PENDING,
    COMPLETED,
    FAILED
}
