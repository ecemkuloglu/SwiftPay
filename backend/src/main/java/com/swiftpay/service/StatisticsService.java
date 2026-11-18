package com.swiftpay.service;

import com.swiftpay.config.AppProperties;
import com.swiftpay.domain.Money;
import com.swiftpay.dto.MonthlyStatResponse;
import com.swiftpay.repository.MonthlyTransferStats;
import com.swiftpay.repository.TransferRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Monthly statistics of COMPLETED transfers, last 6 months including the current one. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatisticsService {

    static final int MONTHS = 6;

    private final TransferRepository transferRepository;
    private final AppProperties appProperties;
    private final Clock clock;

    /** Newest month first; months without transfers are returned with count 0 and amount 0.00. */
    public List<MonthlyStatResponse> monthly(Long customerId) {
        ZoneId zone = appProperties.timezone();
        YearMonth current = YearMonth.from(LocalDate.ofInstant(clock.instant(), zone));
        YearMonth oldest = current.minusMonths(MONTHS - 1L);
        Instant since = oldest.atDay(1).atStartOfDay(zone).toInstant();

        Map<String, MonthlyTransferStats> byMonth = transferRepository
                .monthlyCompletedStats(customerId, since, zone.getId()).stream()
                .collect(Collectors.toMap(MonthlyTransferStats::getMonth, Function.identity()));

        List<MonthlyStatResponse> result = new ArrayList<>(MONTHS);
        for (int i = 0; i < MONTHS; i++) {
            String month = current.minusMonths(i).toString(); // yyyy-MM
            MonthlyTransferStats stats = byMonth.get(month);
            result.add(stats == null
                    ? new MonthlyStatResponse(month, 0, Money.normalize(BigDecimal.ZERO))
                    : new MonthlyStatResponse(month, stats.getTransferCount(), Money.normalize(stats.getTotalAmount())));
        }
        return result;
    }
}
