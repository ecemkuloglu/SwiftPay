package com.swiftpay.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Row of GET /transfers. */
public record TransferListItem(
        Long id,
        String maskedReceiverIban,
        BigDecimal amount,
        String status,
        Instant createdAt) {
}
