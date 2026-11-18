package com.swiftpay.config;

import jakarta.validation.constraints.NotNull;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Business time zone used for day/month boundaries (limits, statistics, date filters). */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@NotNull ZoneId timezone) {
}
