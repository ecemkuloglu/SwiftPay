package com.swiftpay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swiftpay.domain.Account;
import com.swiftpay.domain.Customer;
import com.swiftpay.dto.CreateTransferRequest;
import com.swiftpay.dto.TransferResult;
import com.swiftpay.exception.BusinessException;
import com.swiftpay.exception.ErrorCode;
import com.swiftpay.support.IntegrationTestBase;
import com.swiftpay.support.SqlCapture;
import com.swiftpay.support.TestIbans;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransferServiceIntegrationTest extends IntegrationTestBase {

    private Customer ecem;
    private Account ecemMain;
    private Account ecemSecond;
    private Account aliMain;

    @BeforeEach
    void fixtures() {
        ecem = customer();
        ecemMain = account(ecem, "600000.00");
        ecemSecond = account(ecem, "10.00");
        aliMain = account(customer(), "0.00");
    }

    // ---- happy path ---------------------------------------------------------------------------

    @Test
    void happyPathMovesMoneyCompletesAndAudits() {
        TransferResult result = transferService.createTransfer(ecem.getId(), newKey(),
                new CreateTransferRequest(ecemMain.getId(), " " + aliMain.getIban().toLowerCase() + " ",
                        new BigDecimal("1500"), "  Kira "));

        assertThat(result.replayed()).isFalse();
        assertThat(result.transfer().status()).isEqualTo("COMPLETED");
        assertThat(result.transfer().amount()).isEqualTo(new BigDecimal("1500.00"));
        assertThat(result.transfer().description()).isEqualTo("Kira");
        assertThat(result.transfer().receiverIban()).isEqualTo(aliMain.getIban());
        assertThat(result.transfer().maskedReceiverIban()).isEqualTo(
                aliMain.getIban().substring(0, 4) + " **** **** **** **** " + aliMain.getIban().substring(22));
        assertThat(result.transfer().createdAt()).isEqualTo(clock.instant());
        assertThat(result.transfer().completedAt()).isEqualTo(clock.instant());
        assertThat(result.transfer().failureReason()).isNull();

        assertThat(balance(ecemMain)).isEqualByComparingTo("598500.00");
        assertThat(balance(aliMain)).isEqualByComparingTo("1500.00");

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM transfer WHERE id = ?", result.transfer().id());
        assertThat(row.get("status")).isEqualTo("COMPLETED");
        assertThat(row.get("receiver_account_id")).isEqualTo(aliMain.getId());

        Map<String, Object> audit = jdbc.queryForMap("SELECT * FROM audit_log WHERE transfer_id = ?",
                result.transfer().id());
        assertThat(audit.get("event_type")).isEqualTo("TRANSFER_COMPLETED");
        assertThat(audit.get("trace_id")).isEqualTo("n/a");
        assertThat((String) audit.get("details")).doesNotContain(aliMain.getIban()).contains("****");
    }

    @Test
    void transferBetweenOwnAccountsIsAllowed() {
        assertThat(transfer(ecem, ecemMain, ecemSecond, "5.00")).isNull();
        assertThat(balance(ecemSecond)).isEqualByComparingTo("15.00");
    }

    // ---- business failures: stored as FAILED, nothing moves, not counted -----------------------

    @Test
    void insufficientBalanceIsStoredAsFailed() {
        assertFailedAndNothingMoved(ecemSecond, aliMain, "10.01", ErrorCode.INSUFFICIENT_BALANCE);
    }

    @Test
    void perTransferLimitIsStoredAsFailed() {
        assertFailedAndNothingMoved(ecemMain, aliMain, "50000.01", ErrorCode.PER_TRANSFER_LIMIT_EXCEEDED);
    }

    @Test
    void sameAccountIsStoredAsFailed() {
        assertFailedAndNothingMoved(ecemMain, ecemMain, "1.00", ErrorCode.SAME_ACCOUNT_TRANSFER);
    }

    @Test
    void unknownReceiverIsStoredAsFailedWithoutReceiverId() {
        String unknownIban = TestIbans.next();
        BigDecimal before = balance(ecemMain);

        assertThatThrownBy(() -> transferService.createTransfer(ecem.getId(), "k-unknown",
                new CreateTransferRequest(ecemMain.getId(), unknownIban, new BigDecimal("1.00"), null)))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.RECEIVER_NOT_FOUND);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT * FROM transfer WHERE customer_id = ? AND idempotency_key = 'k-unknown'", ecem.getId());
        assertThat(row.get("status")).isEqualTo("FAILED");
        assertThat(row.get("failure_code")).isEqualTo("RECEIVER_NOT_FOUND");
        assertThat(row.get("receiver_account_id")).isNull();
        assertThat(balance(ecemMain)).isEqualByComparingTo(before);
    }

    @Test
    void foreignOrMissingSourceAccountIsRejectedWithoutTransferRowButAudited() {
        assertThat(transfer(ecem, aliMain, ecemMain, "1.00")).isEqualTo(ErrorCode.ACCOUNT_NOT_OWNED);
        Account missing = account(ecem, "0");
        accountRepository.deleteById(missing.getId());
        assertThat(transfer(ecem, missing, aliMain, "1.00")).isEqualTo(ErrorCode.ACCOUNT_NOT_OWNED);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transfer WHERE customer_id = ?", Long.class,
                ecem.getId())).isZero();
        assertThat(jdbc.queryForList("SELECT event_type FROM audit_log WHERE customer_id = ?", String.class,
                ecem.getId())).containsExactly("TRANSFER_REJECTED", "TRANSFER_REJECTED");
    }

    @Test
    void failedTransfersDoNotCountTowardLimits() {
        assertThat(transfer(ecem, ecemMain, aliMain, "50000.01")).isEqualTo(ErrorCode.PER_TRANSFER_LIMIT_EXCEEDED);
        assertThat(transfer(ecem, ecemSecond, aliMain, "40000.00")).isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);

        assertThat(transfer(ecem, ecemMain, aliMain, "50000.00")).isNull();
        assertThat(transfer(ecem, ecemMain, aliMain, "50000.00")).isNull();   // daily total exactly 100,000
        assertThat(countTransfers(ecem, "FAILED")).isEqualTo(2);
    }

    // ---- limit boundaries end to end -------------------------------------------------------

    @Test
    void perTransferLimitBoundary() {
        assertThat(transfer(ecem, ecemMain, aliMain, "50000.00")).isNull();
        assertThat(transfer(ecem, ecemMain, aliMain, "50000.01")).isEqualTo(ErrorCode.PER_TRANSFER_LIMIT_EXCEEDED);
    }

    @Test
    void dailyLimitBoundaryAcrossAccounts() {
        Account ecemThird = account(ecem, "200000.00");
        assertThat(transfer(ecem, ecemMain, aliMain, "50000.00")).isNull();
        assertThat(transfer(ecem, ecemThird, aliMain, "49999.99")).isNull();
        assertThat(transfer(ecem, ecemMain, aliMain, "0.01")).isNull();          // exactly 100,000.00
        assertThat(transfer(ecem, ecemThird, aliMain, "0.01")).isEqualTo(ErrorCode.DAILY_LIMIT_EXCEEDED);
    }

    @Test
    void dailyLimitResetsAtIstanbulMidnight() {
        clock.set(Instant.parse("2026-05-14T20:59:59Z"));                        // 23:59:59 Istanbul
        assertThat(transfer(ecem, ecemMain, aliMain, "50000.00")).isNull();
        assertThat(transfer(ecem, ecemMain, aliMain, "50000.00")).isNull();
        assertThat(transfer(ecem, ecemMain, aliMain, "0.01")).isEqualTo(ErrorCode.DAILY_LIMIT_EXCEEDED);

        clock.set(Instant.parse("2026-05-14T21:00:00Z"));                        // 00:00:00 next day
        assertThat(transfer(ecem, ecemMain, aliMain, "0.01")).isNull();
    }

    @Test
    void monthlyLimitBoundaryAndRollover() {
        Account rich = account(ecem, "1000000.00");
        for (int day = 0; day < 5; day++) {                                      // May 25..29, 100,000 per day
            clock.set(Instant.parse("2026-05-25T09:00:00Z").plusSeconds(86_400L * day));
            assertThat(transfer(ecem, rich, aliMain, "50000.00")).isNull();
            assertThat(transfer(ecem, rich, aliMain, "50000.00")).isNull();
        }
        clock.set(Instant.parse("2026-05-31T20:59:59Z"));                        // May 31 23:59:59 Istanbul
        assertThat(transfer(ecem, rich, aliMain, "0.01")).isEqualTo(ErrorCode.MONTHLY_LIMIT_EXCEEDED);

        clock.set(Instant.parse("2026-05-31T21:00:00Z"));                        // June 1 00:00 Istanbul
        assertThat(transfer(ecem, rich, aliMain, "0.01")).isNull();
    }

    // ---- idempotency ---------------------------------------------------------------------------

    @Test
    void sameKeyAndPayloadIsReplayedWithoutASecondTransfer() {
        TransferResult first = transferService.createTransfer(ecem.getId(), "key-1", request(ecemMain, aliMain, "100"));
        TransferResult second = transferService.createTransfer(ecem.getId(), "key-1",
                request(ecemMain, aliMain, "100.00"));

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.transfer()).isEqualTo(first.transfer());
        assertThat(countTransfers(ecem, "COMPLETED")).isEqualTo(1);
        assertThat(balance(ecemMain)).isEqualByComparingTo("599900.00");
    }

    @Test
    void sameKeyWithDifferentPayloadIsAConflict() {
        transferService.createTransfer(ecem.getId(), "key-2", request(ecemMain, aliMain, "100.00"));

        assertThatThrownBy(() -> transferService.createTransfer(ecem.getId(), "key-2",
                request(ecemMain, aliMain, "100.01")))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        assertThat(balance(ecemMain)).isEqualByComparingTo("599900.00");
    }

    @Test
    void sameKeyForAFailedTransferReturnsTheSameErrorAgain() {
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> transferService.createTransfer(ecem.getId(), "key-3",
                    request(ecemSecond, aliMain, "10.01")))
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);
        }
        assertThat(countTransfers(ecem, "FAILED")).isEqualTo(1);
    }

    @Test
    void sameKeyForTwoCustomersIsIndependent() {
        Customer ali = customerRepository.findById(aliMain.getCustomerId()).orElseThrow();
        Account aliRich = account(ali, "100.00");

        TransferResult ecemResult = transferService.createTransfer(ecem.getId(), "shared",
                request(ecemMain, aliMain, "1.00"));
        TransferResult aliResult = transferService.createTransfer(ali.getId(), "shared",
                request(aliRich, ecemMain, "1.00"));

        assertThat(aliResult.replayed()).isFalse();
        assertThat(aliResult.transfer().id()).isNotEqualTo(ecemResult.transfer().id());
    }

    @Test
    void missingOrTooLongIdempotencyKeyIsRejectedBeforeAnyWork() {
        assertThatThrownBy(() -> transferService.createTransfer(ecem.getId(), " ", request(ecemMain, aliMain, "1")))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        assertThatThrownBy(() -> transferService.createTransfer(ecem.getId(), "k".repeat(65),
                request(ecemMain, aliMain, "1")))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transfer WHERE customer_id = ?", Long.class,
                ecem.getId())).isZero();
    }

    // ---- lock order ----------------------------------------------------------------------------

    @Test
    void locksCustomerFirstThenAccountsInAscendingIdOrder() {
        SqlCapture.clear();
        // receiver has the LOWER id here, so the lock must still list it first
        Customer other = customer();
        Account lowId = aliMain;
        Account highId = account(other, "100.00");
        SqlCapture.clear();

        transfer(other, highId, lowId, "1.00");

        // StatementInspector sees Hibernate-generated SQL only (not the native INSERT); the insert's position
        // between the two locks is verified with Mockito InOrder in TransferFlowIntegrationTest.
        List<String> locks = SqlCapture.statements().stream()
                .map(String::toLowerCase)
                .filter(s -> s.contains("for no key update"))
                .toList();
        locks.forEach(s -> System.out.println("LOCK ORDER SQL: " + s));

        assertThat(locks).hasSize(2);
        assertThat(locks.get(0)).contains("from customer").endsWith("for no key update");
        assertThat(locks.get(1)).contains("from account").contains("in (?,?)")
                .endsWith("order by a1_0.id for no key update");
        assertThat(lowId.getId()).isLessThan(highId.getId());
    }

    private void assertFailedAndNothingMoved(Account source, Account receiver, String amount, ErrorCode expected) {
        BigDecimal sourceBefore = balance(source);
        BigDecimal receiverBefore = balance(receiver);

        assertThat(transfer(ecem, source, receiver, amount)).isEqualTo(expected);

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM transfer WHERE customer_id = ?", ecem.getId());
        assertThat(row.get("status")).isEqualTo("FAILED");
        assertThat(row.get("failure_code")).isEqualTo(expected.name());
        assertThat(row.get("completed_at")).isNull();
        assertThat(balance(source)).isEqualByComparingTo(sourceBefore);
        assertThat(balance(receiver)).isEqualByComparingTo(receiverBefore);
        assertThat(jdbc.queryForObject("SELECT event_type FROM audit_log WHERE transfer_id = ?", String.class,
                row.get("id"))).isEqualTo("TRANSFER_FAILED");
    }
}
