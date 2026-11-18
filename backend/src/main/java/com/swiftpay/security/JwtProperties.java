package com.swiftpay.security;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings from the environment ({@code JWT_SECRET}, {@code JWT_EXPIRATION_SECONDS}). Checked when the
 * application starts: a missing, short (&lt; 32 bytes, HS256 minimum) or placeholder secret stops the startup.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long expirationSeconds) {

    static final int MIN_SECRET_BYTES = 32;
    /** The value shipped in .env.example; never acceptable as a real secret. */
    static final String PLACEHOLDER = "change-me-to-a-long-random-secret-at-least-32-bytes";

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is not set");
        }
        if (PLACEHOLDER.equals(secret) || secret.startsWith("change-me")) {
            throw new IllegalStateException("JWT_SECRET still has the placeholder value from .env.example");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes");
        }
        if (expirationSeconds <= 0) {
            throw new IllegalStateException("JWT_EXPIRATION_SECONDS must be positive");
        }
    }

    @Override
    public String toString() {
        return "JwtProperties[secret=****, expirationSeconds=" + expirationSeconds + "]";
    }
}
