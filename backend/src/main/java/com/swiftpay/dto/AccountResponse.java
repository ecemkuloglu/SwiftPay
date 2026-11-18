package com.swiftpay.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;

/** Row of GET /accounts. The full IBAN is returned only to the owner. */
public record AccountResponse(
        Long id,
        String iban,
        String maskedIban,
        String currency,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal balance) {
}
