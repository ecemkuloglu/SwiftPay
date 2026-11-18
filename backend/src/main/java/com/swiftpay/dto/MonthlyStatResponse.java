package com.swiftpay.dto;

import java.math.BigDecimal;

/** Element of GET /statistics/monthly; month is yyyy-MM in Europe/Istanbul. */
public record MonthlyStatResponse(
        String month,
        long totalTransferCount,
        BigDecimal totalTransferredAmount) {
}
