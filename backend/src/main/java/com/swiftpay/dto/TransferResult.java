package com.swiftpay.dto;

/** Outcome of createTransfer; {@code replayed=true} maps to HTTP 200 + {@code Idempotent-Replayed: true}. */
public record TransferResult(TransferResponse transfer, boolean replayed) {
}
