package com.swiftpay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swiftpay.domain.Account;
import com.swiftpay.domain.Customer;
import com.swiftpay.dto.AccountResponse;
import com.swiftpay.dto.LimitsResponse;
import com.swiftpay.dto.MonthlyStatResponse;
import com.swiftpay.dto.PageResponse;
import com.swiftpay.dto.TransferResponse;
import com.swiftpay.dto.TransferSummary;
import com.swiftpay.exception.BusinessException;
import com.swiftpay.exception.ErrorCode;
import com.swiftpay.support.IntegrationTestBase;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class QueryServicesIntegrationTest extends IntegrationTestBase {

    @Autowired
    private TransferQueryService transferQueryService;
    @Autowired
    private StatisticsService statisticsService;
    @Autowired
    private AccountService accountService;

    private Customer ecem;
    private Account source;
    private Account receiver;
    private Customer ali;

    @BeforeEach
    void fixtures() {
        ecem = customer();
        source = account(ecem, "100000.00");
        ali = customer();
        receiver = account(ali, "0.00");
    }

    private Long transferAt(String instant, String amount) {
        clock.set(Instant.parse(instant));
        return transferService.createTransfer(ecem.getId(), newKey(), request(source, receiver, amount))
                .transfer().id();
    }

    // ---- statistics ----------------------------------------------------------------------------

    @Test
    void monthlyStatisticsUseIstanbulMonthsAndZeroFill() {
        transferAt("2026-04-30T20:59:59Z", "50.00");    // Apr 30 23:59:59 Istanbul -> April
        transferAt("2026-04-30T21:30:00Z", "100.00");   // May 1 00:30 Istanbul     -> May
        transferAt("2026-05-10T12:00:00Z", "25.50");
        transferAt("2025-11-30T20:59:59Z", "7.00");     // Nov 30 Istanbul, outside the 6-month window
        clock.set(Instant.parse("2026-05-10T13:00:00Z"));
        transfer(ecem, source, receiver, "60000.00");   // FAILED (per-transfer) -> not counted

        clock.set(Instant.parse("2026-05-14T09:30:00Z"));
        List<MonthlyStatResponse> stats = statisticsService.monthly(ecem.getId());

        assertThat(stats).containsExactly(
                new MonthlyStatResponse("2026-05", 2, new BigDecimal("125.50")),
                new MonthlyStatResponse("2026-04", 1, new BigDecimal("50.00")),
                new MonthlyStatResponse("2026-03", 0, new BigDecimal("0.00")),
                new MonthlyStatResponse("2026-02", 0, new BigDecimal("0.00")),
                new MonthlyStatResponse("2026-01", 0, new BigDecimal("0.00")),
                new MonthlyStatResponse("2025-12", 0, new BigDecimal("0.00")));
        assertThat(statisticsService.monthly(ali.getId())).allSatisfy(s -> assertThat(s.totalTransferCount()).isZero());
    }

    // ---- list + date range ---------------------------------------------------------------------

    @Test
    void dateRangeIsInclusiveInIstanbulTime() {
        Long apr30 = transferAt("2026-04-30T20:59:59Z", "1.00");   // Apr 30 Istanbul
        Long may1 = transferAt("2026-04-30T21:00:00Z", "2.00");    // May 1 00:00 Istanbul
        Long may2 = transferAt("2026-05-02T20:59:59Z", "3.00");    // May 2 23:59:59 Istanbul
        Long may3 = transferAt("2026-05-02T21:00:00Z", "4.00");    // May 3 00:00 Istanbul

        assertThat(ids(LocalDate.parse("2026-05-01"), LocalDate.parse("2026-05-02"))).containsExactly(may2, may1);
        assertThat(ids(LocalDate.parse("2026-05-01"), null)).containsExactly(may3, may2, may1);
        assertThat(ids(null, LocalDate.parse("2026-04-30"))).containsExactly(apr30);
        assertThat(ids(null, null)).containsExactly(may3, may2, may1, apr30);
        assertThat(ids(LocalDate.parse("2026-05-03"), LocalDate.parse("2026-05-03"))).containsExactly(may3);
    }

    @Test
    void listIncludesFailedTransfersAndIsScopedToTheCaller() {
        transferAt("2026-05-01T10:00:00Z", "1.00");
        transfer(ecem, source, receiver, "60000.00");                // FAILED

        PageResponse<TransferSummary> page = transferQueryService.list(ecem.getId(), null, null, 0, 10);

        assertThat(page.content()).extracting(TransferSummary::status).containsExactlyInAnyOrder("COMPLETED", "FAILED");
        assertThat(page.content()).allSatisfy(s -> assertThat(s.maskedReceiverIban()).contains("****"));
        assertThat(transferQueryService.list(ali.getId(), null, null, 0, 10).totalElements()).isZero();
    }

    @Test
    void invalidRangeAndPagingAreRejectedAndSizeIsCapped() {
        assertError(() -> transferQueryService.list(ecem.getId(), LocalDate.parse("2026-05-02"),
                LocalDate.parse("2026-05-01"), 0, 10), ErrorCode.INVALID_DATE_RANGE);
        assertError(() -> transferQueryService.list(ecem.getId(), null, null, -1, 10), ErrorCode.VALIDATION_ERROR);
        assertError(() -> transferQueryService.list(ecem.getId(), null, null, 0, 0), ErrorCode.VALIDATION_ERROR);

        assertThat(transferQueryService.list(ecem.getId(), null, null, 0, 500).size()).isEqualTo(100);
    }

    @Test
    void pagingMetadata() {
        for (int i = 0; i < 3; i++) {
            transferAt("2026-05-01T10:00:0" + i + "Z", "1.00");
        }

        PageResponse<TransferSummary> page = transferQueryService.list(ecem.getId(), null, null, 1, 2);

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.content()).hasSize(1);
    }

    // ---- detail --------------------------------------------------------------------------------

    @Test
    void getByIdIsScopedToTheOwner() {
        Long id = transferAt("2026-05-01T10:00:00Z", "1.00");

        TransferResponse detail = transferQueryService.getById(ecem.getId(), id);
        assertThat(detail.sourceAccountMaskedIban()).isEqualTo(
                source.getIban().substring(0, 4) + " **** **** **** **** " + source.getIban().substring(22));
        assertThat(detail.failureReason()).isNull();

        assertError(() -> transferQueryService.getById(ali.getId(), id), ErrorCode.TRANSFER_NOT_FOUND);
        assertError(() -> transferQueryService.getById(ecem.getId(), Long.MAX_VALUE), ErrorCode.TRANSFER_NOT_FOUND);
    }

    @Test
    void failedTransferDetailHasFailureReason() {
        transfer(ecem, source, receiver, "60000.00");
        Long id = jdbc.queryForObject("SELECT id FROM transfer WHERE customer_id = ?", Long.class, ecem.getId());

        assertThat(transferQueryService.getById(ecem.getId(), id).failureReason())
                .isEqualTo("PER_TRANSFER_LIMIT_EXCEEDED");
    }

    // ---- accounts + limits ---------------------------------------------------------------------

    @Test
    void accountsAndLimitsSnapshot() {
        Account second = account(ecem, "10.5");
        transferAt("2026-05-14T08:00:00Z", "35000.00");
        transferAt("2026-05-02T08:00:00Z", "5000.00");
        transfer(ecem, source, receiver, "60000.00");          // FAILED -> not in "used"
        clock.set(Instant.parse("2026-05-14T09:30:00Z"));

        List<AccountResponse> accounts = accountService.listAccounts(ecem.getId());
        assertThat(accounts).extracting(AccountResponse::id).containsExactly(source.getId(), second.getId());
        assertThat(accounts.get(1).balance()).isEqualTo(new BigDecimal("10.50"));
        assertThat(accounts.get(0).maskedIban()).endsWith(source.getIban().substring(22));

        LimitsResponse limits = accountService.getLimits(ecem.getId());
        assertThat(limits.daily()).isEqualTo(new LimitsResponse.Usage(new BigDecimal("100000.00"),
                new BigDecimal("35000.00"), new BigDecimal("65000.00")));
        assertThat(limits.monthly().used()).isEqualTo(new BigDecimal("40000.00"));
        assertThat(limits.monthly().remaining()).isEqualTo(new BigDecimal("460000.00"));
    }

    private List<Long> ids(LocalDate start, LocalDate end) {
        return transferQueryService.list(ecem.getId(), start, end, 0, 100).content().stream()
                .map(TransferSummary::id).toList();
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(expected);
    }
}
