package com.swiftpay.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;

/** GET /accounts/limits. {@code used} counts only COMPLETED transfers. */
public record LimitsResponse(
        String currency,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal perTransferLimit,
        Usage daily,
        Usage monthly) {

    public record Usage(
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal limit,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal used,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal remaining) {
    }
}
