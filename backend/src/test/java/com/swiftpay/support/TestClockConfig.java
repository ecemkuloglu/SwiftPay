package com.swiftpay.support;

import java.time.Instant;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfig {

    public static final Instant DEFAULT_NOW = Instant.parse("2026-05-14T09:30:00Z");

    @Bean
    @Primary
    public MutableClock testClock() {
        return new MutableClock(DEFAULT_NOW, ZoneId.of("Europe/Istanbul"));
    }
}
