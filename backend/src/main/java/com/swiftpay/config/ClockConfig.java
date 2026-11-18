package com.swiftpay.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Single time source in the business time zone (Europe/Istanbul by default).
 * Services inject {@link Clock} so tests can fix the time; {@code Instant}s stay UTC.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(AppProperties appProperties) {
        return Clock.system(appProperties.timezone());
    }
}
