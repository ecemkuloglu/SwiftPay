package com.swiftpay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.swiftpay.config.AppProperties;
import com.swiftpay.dto.MonthlyStatResponse;
import com.swiftpay.repository.MonthlyTransferStats;
import com.swiftpay.repository.TransferRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    private static final String ISTANBUL = "Europe/Istanbul";

    @Mock
    private TransferRepository transferRepository;

    private StatisticsService serviceAt(String instant) {
        return new StatisticsService(transferRepository, new AppProperties(ZoneId.of(ISTANBUL)),
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    private static MonthlyTransferStats row(String month, long count, String total) {
        return new MonthlyTransferStats() {
            public String getMonth() {
                return month;
            }

            public long getTransferCount() {
                return count;
            }

            public BigDecimal getTotalAmount() {
                return new BigDecimal(total);
            }
        };
    }

    @Test
    void sixMonthsNewestFirstWithZeroFilledGaps() {
        // since = 2025-12-01 00:00 Istanbul
        when(transferRepository.monthlyCompletedStats(1L, Instant.parse("2025-11-30T21:00:00Z"), ISTANBUL))
                .thenReturn(List.of(row("2026-05", 4, "12450"), row("2026-04", 2, "1800.00"),
                        row("2025-12", 1, "0.01")));

        List<MonthlyStatResponse> stats = serviceAt("2026-05-14T09:30:00Z").monthly(1L);

        assertThat(stats).containsExactly(
                new MonthlyStatResponse("2026-05", 4, new BigDecimal("12450.00")),
                new MonthlyStatResponse("2026-04", 2, new BigDecimal("1800.00")),
                new MonthlyStatResponse("2026-03", 0, new BigDecimal("0.00")),
                new MonthlyStatResponse("2026-02", 0, new BigDecimal("0.00")),
                new MonthlyStatResponse("2026-01", 0, new BigDecimal("0.00")),
                new MonthlyStatResponse("2025-12", 1, new BigDecimal("0.01")));
    }

    @Test
    void currentMonthIsTakenInIstanbulTime() {
        // 2026-05-31T21:30Z is already June 1 in Istanbul -> window is 2026-01 .. 2026-06
        when(transferRepository.monthlyCompletedStats(1L, Instant.parse("2025-12-31T21:00:00Z"), ISTANBUL))
                .thenReturn(List.of());

        List<MonthlyStatResponse> stats = serviceAt("2026-05-31T21:30:00Z").monthly(1L);

        assertThat(stats).extracting(MonthlyStatResponse::month)
                .containsExactly("2026-06", "2026-05", "2026-04", "2026-03", "2026-02", "2026-01");
        assertThat(stats).allSatisfy(s -> {
            assertThat(s.totalTransferCount()).isZero();
            assertThat(s.totalTransferredAmount()).isEqualTo(new BigDecimal("0.00"));
        });
    }

    @Test
    void windowCrossesTheYearBoundary() {
        when(transferRepository.monthlyCompletedStats(1L, Instant.parse("2025-07-31T21:00:00Z"), ISTANBUL))
                .thenReturn(List.of());

        assertThat(serviceAt("2026-01-10T10:00:00Z").monthly(1L)).extracting(MonthlyStatResponse::month)
                .containsExactly("2026-01", "2025-12", "2025-11", "2025-10", "2025-09", "2025-08");
    }
}
