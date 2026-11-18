package com.swiftpay.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Transfer object of POST /transfers and GET /transfers/{id}. {@code sourceAccountMaskedIban} is only set for
 * the detail endpoint and {@code failureReason} only for FAILED transfers; both are omitted when null.
 */
@JsonPropertyOrder({"id", "sourceAccountId", "sourceAccountMaskedIban", "receiverIban", "maskedReceiverIban",
        "amount", "currency", "description", "status", "createdAt", "completedAt", "failureReason"})
public record TransferResponse(
        Long id,
        Long sourceAccountId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String sourceAccountMaskedIban,
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
