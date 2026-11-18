package com.swiftpay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.swiftpay.config.AppProperties;
import com.swiftpay.config.TransferLimitsProperties;
import com.swiftpay.dto.LimitsResponse;
import com.swiftpay.repository.TransferRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LimitServiceTest {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static final TransferLimitsProperties LIMITS = new TransferLimitsProperties(
            new BigDecimal("50000.00"), new BigDecimal("100000.00"), new BigDecimal("500000.00"));

    @Mock
    private TransferRepository transferRepository;

    private LimitService serviceAt(String instant) {
        // UTC clock on purpose: boundaries must come from app.timezone, not from the clock's zone
        Clock clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
        return new LimitService(transferRepository, LIMITS, new AppProperties(ISTANBUL), clock);
    }

    // ---- the rule ------------------------------------------------------------------------

    @Test
    void exactlyReachingTheLimitPasses() {
        assertThat(LimitService.withinLimit(new BigDecimal("50000.00"), new BigDecimal("50000.00"),
                new BigDecimal("100000.00"))).isTrue();
    }

    @Test
    void oneCentOverTheLimitFails() {
        assertThat(LimitService.withinLimit(new BigDecimal("50000.00"), new BigDecimal("50000.01"),
                new BigDecimal("100000.00"))).isFalse();
    }

    @Test
    void ruleUsesCompareToNotEquals() {
        // 100000.0 vs 100000.00: equals() would say different, compareTo() says equal
        assertThat(LimitService.withinLimit(new BigDecimal("0"), new BigDecimal("100000.0"),
                new BigDecimal("100000.00"))).isTrue();
    }

    // ---- day / month windows -------------------------------------------------------------

    @Test
    void lastSecondOfTheDayStillBelongsToThatDay() {
        LimitService service = serviceAt("2026-05-14T20:59:59Z"); // 23:59:59 Istanbul

        LimitService.Window day = service.dayOf(Instant.parse("2026-05-14T20:59:59Z"));

        assertThat(day.start()).isEqualTo(Instant.parse("2026-05-13T21:00:00Z"));
        assertThat(day.end()).isEqualTo(Instant.parse("2026-05-14T21:00:00Z"));
    }

    @Test
    void midnightStartsANewDayButNotANewMonth() {
        LimitService service = serviceAt("2026-05-14T21:00:00Z");
        Instant before = Instant.parse("2026-05-14T20:59:59Z");   // 23:59:59 Istanbul
        Instant midnight = Instant.parse("2026-05-14T21:00:00Z"); // 00:00:00 Istanbul, May 15

        assertThat(service.dayOf(midnight).start()).isEqualTo(midnight);
        assertThat(service.dayOf(midnight)).isNotEqualTo(service.dayOf(before));
        assertThat(service.monthOf(midnight)).isEqualTo(service.monthOf(before));
    }

    @Test
    void monthRollsOverAtIstanbulMidnight() {
        LimitService service = serviceAt("2026-05-31T21:00:00Z");
        Instant lastSecondOfMay = Instant.parse("2026-05-31T20:59:59Z"); // May 31 23:59:59 Istanbul
        Instant firstOfJune = Instant.parse("2026-05-31T21:00:00Z");     // June 1 00:00 Istanbul

        assertThat(service.monthOf(lastSecondOfMay))
                .isEqualTo(new LimitService.Window(Instant.parse("2026-04-30T21:00:00Z"), firstOfJune));
        assertThat(service.monthOf(firstOfJune))
                .isEqualTo(new LimitService.Window(firstOfJune, Instant.parse("2026-06-30T21:00:00Z")));
    }

    @Test
    void usedTodayAndThisMonthQueryTheIstanbulWindowsOfTheClock() {
        LimitService service = serviceAt("2026-05-14T20:59:59Z");
        when(transferRepository.sumCompletedAmount(1L, Instant.parse("2026-05-13T21:00:00Z"),
                Instant.parse("2026-05-14T21:00:00Z"))).thenReturn(new BigDecimal("35000.00"));
        when(transferRepository.sumCompletedAmount(1L, Instant.parse("2026-04-30T21:00:00Z"),
                Instant.parse("2026-05-31T21:00:00Z"))).thenReturn(new BigDecimal("120000"));

        assertThat(service.usedToday(1L)).isEqualTo(new BigDecimal("35000.00"));
        assertThat(service.usedThisMonth(1L)).isEqualTo(new BigDecimal("120000.00"));
    }

    // ---- snapshot ------------------------------------------------------------------------

    @Test
    void snapshotHasLimitUsedRemainingAtScaleTwo() {
        LimitService service = serviceAt("2026-05-14T09:30:00Z");
        when(transferRepository.sumCompletedAmount(eq(1L), any(), any()))
                .thenReturn(new BigDecimal("35000"), new BigDecimal("120000.00"));

        LimitsResponse snapshot = service.getLimitsSnapshot(1L);

        assertThat(snapshot.currency()).isEqualTo("TRY");
        assertThat(snapshot.perTransferLimit()).isEqualTo(new BigDecimal("50000.00"));
        assertThat(snapshot.daily()).isEqualTo(new LimitsResponse.Usage(
                new BigDecimal("100000.00"), new BigDecimal("35000.00"), new BigDecimal("65000.00")));
        assertThat(snapshot.monthly()).isEqualTo(new LimitsResponse.Usage(
                new BigDecimal("500000.00"), new BigDecimal("120000.00"), new BigDecimal("380000.00")));
    }

    @Test
    void remainingNeverGoesNegative() {
        LimitService service = serviceAt("2026-05-14T09:30:00Z");
        when(transferRepository.sumCompletedAmount(eq(1L), any(), any()))
                .thenReturn(new BigDecimal("100000.00"), new BigDecimal("0"));

        assertThat(service.getLimitsSnapshot(1L).daily().remaining()).isEqualTo(new BigDecimal("0.00"));
    }
}
