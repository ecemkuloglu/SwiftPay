package com.swiftpay.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;

/** Transfer object of POST /transfers and GET /transfers/{id}. Money is serialised as a string. */
public record TransferResponse(
        Long id,
        Long sourceAccountId,
        String sourceAccountMaskedIban,
        String receiverIban,
        String maskedReceiverIban,
        BigDecimal amount,
        String currency,
        String description,
        String status,
        Instant createdAt,
        Instant completedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String failureReason) {
}
