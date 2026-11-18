package com.swiftpay.config;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Global transfer limits in TRY (application.yml {@code app.limits.*}); the app fails to start if invalid. */
@Validated
@ConfigurationProperties(prefix = "app.limits")
public record TransferLimitsProperties(
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal perTransfer,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal daily,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal monthly) {
}
