package com.swiftpay.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/** Issues and verifies HS256 access tokens ({@code sub} = customer id). Time comes from the injected Clock. */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationSeconds;
    private final Clock clock;
    private final JwtParser parser;

    public JwtService(JwtProperties properties, Clock clock) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = properties.expirationSeconds();
        this.clock = clock;
        this.parser = Jwts.parser()
                .verifyWith(key)                       // HMAC only; unsigned ("alg":"none") tokens are rejected
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    public String issue(Long customerId) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(customerId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public long expirationSeconds() {
        return expirationSeconds;
    }

    /** Customer id of a valid token; empty for expired, tampered, wrongly signed or malformed tokens. */
    public Optional<Long> verify(String token) {
        try {
            return Optional.of(Long.valueOf(parser.parseSignedClaims(token).getPayload().getSubject()));
        } catch (JwtException | IllegalArgumentException e) {   // NumberFormatException is an IllegalArgumentException
            return Optional.empty();
        }
    }
}
