package com.swiftpay.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swiftpay.domain.Account;
import com.swiftpay.domain.Customer;
import com.swiftpay.domain.Transfer;
import com.swiftpay.domain.TransferStatus;
import com.swiftpay.support.RepositoryTestBase;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class TransferRepositoryTest extends RepositoryTestBase {

    private static final String ISTANBUL = "Europe/Istanbul";
    private static final String HASH = "b".repeat(64);

    @Autowired
    private TransferRepository transferRepository;

    private Customer customer;
    private Account source;
    private Account receiver;

    @BeforeEach
    void setUp() {
        customer = newCustomer();
        source = newAccount(customer, "1000.00");
        receiver = newAccount(newCustomer(), "0.00");
        em.flush();
    }

    // ---- insertPendingIfAbsent -------------------------------------------------------------

    @Test
    void insertPendingIfAbsentInsertsOnceAndIgnoresTheSameKey() {
        assertThat(insert(customer, "key-1", "10.00")).isEqualTo(1);
        assertThat(insert(customer, "key-1", "10.00")).isZero();

        Transfer stored = transferRepository.findByCustomerIdAndIdempotencyKey(customer.getId(), "key-1")
                .orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(TransferStatus.PENDING);
        assertThat(stored.getAmount()).isEqualByComparingTo("10.00");
        assertThat(stored.getCurrency()).isEqualTo("TRY");
        assertThat(stored.getRequestHash()).isEqualTo(HASH);
        assertThat(stored.getCreatedAt()).isEqualTo(T0);
        assertThat(stored.getCompletedAt()).isNull();
    }

    @Test
    void sameKeyForAnotherCustomerIsIndependent() {
        Customer other = newCustomer();
        Account otherSource = newAccount(other, "5.00");
        em.flush();

        assertThat(insert(customer, "shared-key", "10.00")).isEqualTo(1);
        assertThat(transferRepository.insertPendingIfAbsent(other.getId(), otherSource.getId(), receiver.getId(),
                receiver.getIban(), new BigDecimal("1.00"), "x", "shared-key", HASH, T0)).isEqualTo(1);
    }

    @Test
    void insertPendingIfAbsentAcceptsNullReceiverAndDescription() {
        int inserted = transferRepository.insertPendingIfAbsent(customer.getId(), source.getId(), null,
                nextIban(), new BigDecimal("5.00"), null, "key-null", HASH, T0);

        assertThat(inserted).isEqualTo(1);
        Transfer stored = transferRepository.findByCustomerIdAndIdempotencyKey(customer.getId(), "key-null")
                .orElseThrow();
        assertThat(stored.getReceiverAccountId()).isNull();
        assertThat(stored.getDescription()).isNull();
    }

    // ---- DB constraints --------------------------------------------------------------------

    @Test
    void databaseRejectsZeroAmount() {
        assertThatThrownBy(() -> insert(customer, "key-zero", "0.00"))
                .hasStackTraceContaining("ck_transfer_amount_positive");
    }

    @Test
    void databaseRejectsNegativeAmount() {
        assertThatThrownBy(() -> insert(customer, "key-neg", "-1.00"))
                .hasStackTraceContaining("ck_transfer_amount_positive");
    }

    @Test
    void databaseRejectsCompletedWithoutCompletedAt() {
        insert(customer, "key-c", "1.00");

        assertThatThrownBy(() -> em.createNativeQuery(
                        "UPDATE transfer SET status = 'COMPLETED' WHERE idempotency_key = 'key-c'")
                .executeUpdate())
                .hasStackTraceContaining("ck_transfer_completed_at");
    }

    @Test
    void databaseRejectsFailedWithoutFailureCode() {
        insert(customer, "key-f", "1.00");

        assertThatThrownBy(() -> em.createNativeQuery(
                        "UPDATE transfer SET status = 'FAILED' WHERE idempotency_key = 'key-f'")
                .executeUpdate())
                .hasStackTraceContaining("ck_transfer_failure_code");
    }

    @Test
    void entityStateTransitionsArePersisted() {
        Transfer completed = completedTransfer(source, receiver, "12.34", T0);
        Transfer failed = failedTransfer(source, receiver, "99.99", T0);
        flushAndClear();

        Transfer c = transferRepository.findById(completed.getId()).orElseThrow();
        Transfer f = transferRepository.findById(failed.getId()).orElseThrow();
        assertThat(c.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(c.getCompletedAt()).isEqualTo(T0);
        assertThat(f.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(f.getFailureCode()).isEqualTo("INSUFFICIENT_BALANCE");
    }

    // ---- ownership -------------------------------------------------------------------------

    @Test
    void findByIdAndCustomerIdIsScopedToTheOwner() {
        Transfer transfer = completedTransfer(source, receiver, "1.00", T0);
        Customer stranger = newCustomer();
        flushAndClear();

        assertThat(transferRepository.findByIdAndCustomerId(transfer.getId(), customer.getId())).isPresent();
        assertThat(transferRepository.findByIdAndCustomerId(transfer.getId(), stranger.getId())).isEmpty();
    }

    // ---- sumCompletedAmount ----------------------------------------------------------------

    @Test
    void sumCompletedAmountCountsOnlyCompletedInsideHalfOpenRange() {
        Instant from = Instant.parse("2026-05-13T21:00:00Z");   // 2026-05-14 00:00 Istanbul
        Instant to = Instant.parse("2026-05-14T21:00:00Z");     // 2026-05-15 00:00 Istanbul

        completedTransfer(source, receiver, "100.00", from);                  // exactly at from -> in
        completedTransfer(source, receiver, "10.00", to.minusNanos(1_000));   // last microsecond -> in
        completedTransfer(source, receiver, "200.00", to);                    // exactly at to -> out
        completedTransfer(source, receiver, "300.00", from.minusNanos(1_000)); // just before -> out
        failedTransfer(source, receiver, "400.00", from.plusSeconds(10));     // FAILED -> out
        em.persist(pendingTransfer(source, receiver, "500.00", from.plusSeconds(20))); // PENDING -> out
        flushAndClear();

        assertThat(transferRepository.sumCompletedAmount(customer.getId(), from, to))
                .isEqualByComparingTo("110.00");
    }

    @Test
    void sumCompletedAmountIsZeroWhenNothingMatches() {
        BigDecimal sum = transferRepository.sumCompletedAmount(customer.getId(), T0, T0.plusSeconds(60));

        assertThat(sum).isNotNull().isEqualByComparingTo("0");
    }

    // ---- monthly statistics ----------------------------------------------------------------

    @Test
    void monthlyStatsGroupByIstanbulMonth() {
        // 2026-04-30T21:30Z is 2026-05-01 00:30 in Istanbul (UTC+3) -> May
        completedTransfer(source, receiver, "100.00", Instant.parse("2026-04-30T21:30:00Z"));
        // 2026-04-30T20:59:59Z is 2026-04-30 23:59:59 in Istanbul -> April
        completedTransfer(source, receiver, "50.00", Instant.parse("2026-04-30T20:59:59Z"));
        completedTransfer(source, receiver, "25.50", Instant.parse("2026-05-10T12:00:00Z"));
        failedTransfer(source, receiver, "999.00", Instant.parse("2026-05-11T12:00:00Z"));
        // before `since` (2025-12-31 23:59:59 Istanbul) -> excluded
        completedTransfer(source, receiver, "7.00", Instant.parse("2025-12-31T20:59:59Z"));
        // another customer's transfer -> excluded
        Account otherSource = newAccount(newCustomer(), "100.00");
        completedTransfer(otherSource, receiver, "1.00", Instant.parse("2026-05-10T12:00:00Z"));
        flushAndClear();

        Instant since = Instant.parse("2025-12-31T21:00:00Z"); // 2026-01-01 00:00 Istanbul
        List<MonthlyTransferStats> stats =
                transferRepository.monthlyCompletedStats(customer.getId(), since, ISTANBUL);

        assertThat(stats).extracting(MonthlyTransferStats::getMonth).containsExactly("2026-05", "2026-04");
        assertThat(stats.get(0).getTransferCount()).isEqualTo(2);
        assertThat(stats.get(0).getTotalAmount()).isEqualByComparingTo("125.50");
        assertThat(stats.get(1).getTransferCount()).isEqualTo(1);
        assertThat(stats.get(1).getTotalAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void monthlyStatsInUtcWouldPutTheBoundaryTransferInApril() {
        completedTransfer(source, receiver, "100.00", Instant.parse("2026-04-30T21:30:00Z"));
        flushAndClear();

        List<MonthlyTransferStats> stats = transferRepository.monthlyCompletedStats(
                customer.getId(), Instant.parse("2026-01-01T00:00:00Z"), "UTC");

        assertThat(stats).extracting(MonthlyTransferStats::getMonth).containsExactly("2026-04");
    }

    // ---- paged list with optional date range -----------------------------------------------

    @Test
    void listIsScopedSortedNewestFirstAndHonoursOptionalBounds() {
        Instant t1 = Instant.parse("2026-05-01T10:00:00Z");
        Instant t2 = Instant.parse("2026-05-02T10:00:00Z");
        Instant t3 = Instant.parse("2026-05-03T10:00:00Z");
        Transfer first = completedTransfer(source, receiver, "1.00", t1);
        Transfer second = failedTransfer(source, receiver, "2.00", t2);
        Transfer third = completedTransfer(source, receiver, "3.00", t3);
        Account otherSource = newAccount(newCustomer(), "100.00");
        completedTransfer(otherSource, receiver, "4.00", t2);
        flushAndClear();

        PageRequest page = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));

        assertThat(ids(TransferSpecifications.forCustomer(customer.getId(), null, null), page))
                .containsExactly(third.getId(), second.getId(), first.getId());
        assertThat(ids(TransferSpecifications.forCustomer(customer.getId(), t2, null), page))
                .containsExactly(third.getId(), second.getId());
        assertThat(ids(TransferSpecifications.forCustomer(customer.getId(), null, t2), page))
                .containsExactly(first.getId());
        assertThat(ids(TransferSpecifications.forCustomer(customer.getId(), t2, t3), page))
                .containsExactly(second.getId());
    }

    @Test
    void listIsPaged() {
        for (int i = 0; i < 3; i++) {
            completedTransfer(source, receiver, "1.00", T0.plusSeconds(i));
        }
        flushAndClear();

        Page<Transfer> page = transferRepository.findAll(
                TransferSpecifications.forCustomer(customer.getId(), null, null),
                PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getCreatedAt()).isEqualTo(T0);
    }

    // ---- helpers ---------------------------------------------------------------------------

    private int insert(Customer owner, String key, String amount) {
        return transferRepository.insertPendingIfAbsent(owner.getId(), source.getId(), receiver.getId(),
                receiver.getIban(), new BigDecimal(amount), "Kira", key, HASH, T0);
    }

    private List<Long> ids(org.springframework.data.jpa.domain.Specification<Transfer> spec, PageRequest page) {
        return transferRepository.findAll(spec, page).map(Transfer::getId).getContent();
    }
}
