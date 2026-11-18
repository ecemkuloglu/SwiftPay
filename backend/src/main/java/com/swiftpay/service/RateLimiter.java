package com.swiftpay.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.swiftpay.config.RateLimitProperties;
import com.swiftpay.config.RateLimitProperties.Limit;
import com.swiftpay.exception.ErrorCode;
import com.swiftpay.exception.RateLimitExceededException;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.EstimationProbe;
import io.github.bucket4j.TimeMeter;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Token-bucket rate limits (Bucket4j) kept in bounded, expiring in-memory maps (Caffeine).
 * Limitation: buckets live in this JVM only, so limits are per instance and reset on restart.
 * Bucket time comes from the injected Clock, so tests can move time forward to check the refill.
 */
@Component
public class RateLimiter {

    private static final int MAX_KEYS = 100_000;

    private final Limit loginLimit;
    private final Limit transferLimit;
    private final TimeMeter timeMeter;
    private final Cache<String, Bucket> loginFailures;
    private final Cache<Long, Bucket> transfers;

    public RateLimiter(RateLimitProperties properties, Clock clock) {
        this.loginLimit = properties.loginFailures();
        this.transferLimit = properties.transfers();
        this.timeMeter = new ClockTimeMeter(clock);
        this.loginFailures = Caffeine.newBuilder().maximumSize(MAX_KEYS)
                .expireAfterAccess(loginLimit.window()).build();
        this.transfers = Caffeine.newBuilder().maximumSize(MAX_KEYS)
                .expireAfterAccess(transferLimit.window()).build();
    }

    // ---- login: count FAILED attempts per username + client IP ---------------------------------

    /** Throws TOO_MANY_ATTEMPTS while the failure budget for this username + IP is used up. */
    public void checkLogin(String username, String clientIp) {
        Bucket bucket = loginFailures.getIfPresent(loginKey(username, clientIp));
        if (bucket != null) {
            EstimationProbe probe = bucket.estimateAbilityToConsume(1);
            if (!probe.canBeConsumed()) {
                throw new RateLimitExceededException(ErrorCode.TOO_MANY_ATTEMPTS, seconds(probe.getNanosToWaitForRefill()));
            }
        }
    }

    public void recordLoginFailure(String username, String clientIp) {
        loginFailures.get(loginKey(username, clientIp), k -> newBucket(loginLimit)).tryConsume(1);
    }

    /** A successful login starts the failure count from zero again. */
    public void resetLogin(String username, String clientIp) {
        loginFailures.invalidate(loginKey(username, clientIp));
    }

    // ---- transfers: every POST /transfers of a customer -----------------------------------------

    public void consumeTransfer(Long customerId) {
        ConsumptionProbe probe = transfers.get(customerId, k -> newBucket(transferLimit)).tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            throw new RateLimitExceededException(ErrorCode.TOO_MANY_REQUESTS, seconds(probe.getNanosToWaitForRefill()));
        }
    }

    private Bucket newBucket(Limit limit) {
        return Bucket.builder()
                .addLimit(l -> l.capacity(limit.capacity()).refillGreedy(limit.capacity(), limit.window()))
                .withCustomTimePrecision(timeMeter)
                .build();
    }

    private static String loginKey(String username, String clientIp) {
        return (username == null ? "" : username.trim().toLowerCase(Locale.ROOT)) + "|" + clientIp;
    }

    private static long seconds(long nanos) {
        return (nanos + TimeUnit.SECONDS.toNanos(1) - 1) / TimeUnit.SECONDS.toNanos(1);
    }

    private record ClockTimeMeter(Clock clock) implements TimeMeter {

        @Override
        public long currentTimeNanos() {
            Instant now = clock.instant();
            return TimeUnit.SECONDS.toNanos(now.getEpochSecond()) + now.getNano();
        }

        @Override
        public boolean isWallClockBased() {
            return true;
        }
    }
}
