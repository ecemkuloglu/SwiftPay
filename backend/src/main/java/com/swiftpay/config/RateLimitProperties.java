package com.swiftpay.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * In-memory rate limits ({@code app.rate-limit.*}): failed logins per username + client IP, and transfer
 * creations per customer. Each is "at most {@code capacity} within {@code window}", refilled continuously.
 */
@Validated
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(@Valid @NotNull Limit loginFailures, @Valid @NotNull Limit transfers) {

    public record Limit(@Positive int capacity, @NotNull Duration window) {
    }
}
