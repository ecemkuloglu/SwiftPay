package com.swiftpay.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Money helpers shared by the entities. All amounts are TRY with 2 decimals. */
public final class Money {

    public static final int SCALE = 2;
    public static final String CURRENCY = "TRY";

    private Money() {
    }

    /**
     * Validates that {@code amount} is positive and has at most 2 decimals, and returns it at scale 2.
     * Uses compareTo/scale checks, never equals (1.0 and 1.00 are the same amount).
     */
    public static BigDecimal requirePositive(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be greater than 0");
        }
        if (amount.stripTrailingZeros().scale() > SCALE) {
            throw new IllegalArgumentException("amount must have at most 2 decimals");
        }
        return amount.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    /** Returns {@code value} at scale 2 (e.g. a DB sum of 0 becomes 0.00); null becomes 0.00. */
    public static BigDecimal normalize(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(SCALE, RoundingMode.UNNECESSARY);
    }
}
