package com.swiftpay.dto;

import java.math.BigDecimal;

/** GET /accounts/limits. {@code used} counts only COMPLETED transfers. */
public record LimitsResponse(
        String currency,
        BigDecimal perTransferLimit,
        Usage daily,
        Usage monthly) {

    public record Usage(
            BigDecimal limit,
            BigDecimal used,
            BigDecimal remaining) {
    }
}
