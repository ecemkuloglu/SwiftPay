package com.swiftpay.service;

import com.swiftpay.config.AppProperties;
import com.swiftpay.config.TransferLimitsProperties;
import com.swiftpay.domain.Money;
import com.swiftpay.dto.LimitsResponse;
import com.swiftpay.repository.TransferRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Daily/monthly usage in the business time zone. Only COMPLETED transfers count, across ALL of the
 * customer's accounts. Not @Transactional on purpose: it runs inside TransferService's transaction.
 */
@Service
@RequiredArgsConstructor
public class LimitService {

    private final TransferRepository transferRepository;
    private final TransferLimitsProperties limits;
    private final AppProperties appProperties;
    private final Clock clock;

    /** Half-open time window [start, end). */
    public record Window(Instant start, Instant end) {
    }

    /** The rule used by every limit check: used + amount <= limit (exactly reaching the limit is allowed). */
    public static boolean withinLimit(BigDecimal used, BigDecimal amount, BigDecimal limit) {
        return used.add(amount).compareTo(limit) <= 0;
    }

    public Window dayOf(Instant at) {
        ZoneId zone = appProperties.timezone();
        LocalDate day = LocalDate.ofInstant(at, zone);
        return new Window(day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant());
    }

    public Window monthOf(Instant at) {
        ZoneId zone = appProperties.timezone();
        YearMonth month = YearMonth.from(LocalDate.ofInstant(at, zone));
        return new Window(month.atDay(1).atStartOfDay(zone).toInstant(),
                month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant());
    }

    public BigDecimal usedToday(Long customerId) {
        return usedToday(customerId, clock.instant());
    }

    public BigDecimal usedThisMonth(Long customerId) {
        return usedThisMonth(customerId, clock.instant());
    }

    public BigDecimal usedToday(Long customerId, Instant at) {
        return sum(customerId, dayOf(at));
    }

    public BigDecimal usedThisMonth(Long customerId, Instant at) {
        return sum(customerId, monthOf(at));
    }

    public LimitsResponse getLimitsSnapshot(Long customerId) {
        Instant now = clock.instant();
        return new LimitsResponse(
                Money.CURRENCY,
                Money.normalize(limits.perTransfer()),
                usage(limits.daily(), usedToday(customerId, now)),
                usage(limits.monthly(), usedThisMonth(customerId, now)));
    }

    private BigDecimal sum(Long customerId, Window window) {
        return Money.normalize(transferRepository.sumCompletedAmount(customerId, window.start(), window.end()));
    }

    private static LimitsResponse.Usage usage(BigDecimal limit, BigDecimal used) {
        BigDecimal remaining = limit.subtract(used).max(BigDecimal.ZERO);
        return new LimitsResponse.Usage(Money.normalize(limit), Money.normalize(used), Money.normalize(remaining));
    }
}
