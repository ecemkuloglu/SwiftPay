package com.swiftpay.service;

import java.math.BigDecimal;

/** Validated, canonical form of a create-transfer request; the idempotency hash is computed from it. */
public record NormalizedTransferRequest(Long sourceAccountId, String receiverIban, BigDecimal amount,
                                        String description) {
}
