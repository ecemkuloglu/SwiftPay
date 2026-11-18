package com.swiftpay.security;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Explicit allowed origins ({@code CORS_ALLOWED_ORIGINS}, comma separated). A "*" entry is rejected. */
@Validated
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(@NotEmpty List<String> allowedOrigins) {

    public CorsProperties {
        if (allowedOrigins != null && allowedOrigins.stream().anyMatch(o -> o.contains("*"))) {
            throw new IllegalStateException("Wildcard CORS origins are not allowed");
        }
    }
}
