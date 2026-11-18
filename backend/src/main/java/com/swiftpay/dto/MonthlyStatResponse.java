package com.swiftpay.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;

/** Element of GET /statistics/monthly; month is yyyy-MM in Europe/Istanbul. */
public record MonthlyStatResponse(
        String month,
        long totalTransferCount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalTransferredAmount) {
}
