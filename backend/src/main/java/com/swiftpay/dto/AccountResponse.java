package com.swiftpay.dto;

import java.math.BigDecimal;

/** Row of GET /accounts. The full IBAN is returned only to the owner. */
public record AccountResponse(
        Long id,
        String iban,
        String maskedIban,
        String currency,
        BigDecimal balance) {
}
