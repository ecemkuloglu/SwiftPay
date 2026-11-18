package com.swiftpay.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** The app must refuse to start with a weak or missing secret (fail fast at configuration binding). */
class JwtPropertiesTest {

    private static final String GOOD = "0123456789abcdef0123456789abcdef";   // exactly 32 bytes

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "0123456789abcdef0123456789abcde",       // 31 bytes
            JwtProperties.PLACEHOLDER, "change-me-please-change-me-please-change-me"})
    void weakSecretsAreRejected(String secret) {
        assertThatThrownBy(() -> new JwtProperties(secret, 900)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nonPositiveExpiryIsRejected() {
        assertThatThrownBy(() -> new JwtProperties(GOOD, 0)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void thirtyTwoByteSecretIsAcceptedAndNeverPrinted() {
        JwtProperties properties = new JwtProperties(GOOD, 900);
        assertThat(properties.toString()).doesNotContain(GOOD);
    }

    @Test
    void issuedTokenVerifiesAndCarriesTheCustomerId() {
        JwtService service = new JwtService(new JwtProperties(GOOD, 60),
                Clock.fixed(Instant.parse("2026-05-14T09:30:00Z"), ZoneOffset.UTC));

        assertThat(service.verify(service.issue(42L))).contains(42L);
        assertThat(service.verify("garbage")).isEmpty();
        assertThat(service.expirationSeconds()).isEqualTo(60);
    }

    @Test
    void wildcardCorsOriginIsRejected() {
        assertThatThrownBy(() -> new CorsProperties(List.of("http://localhost:3000", "*")))
                .isInstanceOf(IllegalStateException.class);
    }
}
