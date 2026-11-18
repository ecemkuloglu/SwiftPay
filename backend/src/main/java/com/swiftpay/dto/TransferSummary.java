package com.swiftpay.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;

/** Row of GET /transfers. */
public record TransferSummary(
        Long id,
        String maskedReceiverIban,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
        String status,
        Instant createdAt) {
}
