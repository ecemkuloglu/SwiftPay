package com.swiftpay.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swiftpay.config.TransferLimitsProperties;
import com.swiftpay.domain.Account;
import com.swiftpay.exception.BusinessException;
import com.swiftpay.exception.ErrorCode;
import com.swiftpay.service.TransferRuleValidator.TransferCheck;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TransferRuleValidatorTest {

    private static final long CUSTOMER = 1L;
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final TransferRuleValidator validator = new TransferRuleValidator(new TransferLimitsProperties(
            new BigDecimal("50000.00"), new BigDecimal("100000.00"), new BigDecimal("500000.00")));

    private static Account account(long id, long owner, String balance) {
        Account account = new Account(owner, "TR620006100000000000001001", new BigDecimal(balance), Instant.EPOCH);
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    private static final Account SOURCE = account(10L, CUSTOMER, "200000.00");
    private static final Account RECEIVER = account(20L, 2L, "0.00");

    private static TransferCheck check(Account source, Account receiver, String amount, String usedToday,
                                       String usedMonth) {
        return new TransferCheck(CUSTOMER, source, receiver, new BigDecimal(amount), new BigDecimal(usedToday),
                new BigDecimal(usedMonth));
    }

    private void assertFails(TransferCheck check, ErrorCode expected) {
        assertThatThrownBy(() -> validator.validate(check))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    @Test
    void validTransferPasses() {
        assertThatCode(() -> validator.validate(check(SOURCE, RECEIVER, "1500.00", "0", "0")))
                .doesNotThrowAnyException();
    }

    // ---- each rule on its own ---------------------------------------------------------------

    @Test
    void sourceNotOwned() {
        assertFails(check(account(11L, 99L, "100.00"), RECEIVER, "1.00", "0", "0"), ErrorCode.ACCOUNT_NOT_OWNED);
        assertFails(check(null, RECEIVER, "1.00", "0", "0"), ErrorCode.ACCOUNT_NOT_OWNED);
    }

    @Test
    void receiverMissing() {
        assertFails(check(SOURCE, null, "1.00", "0", "0"), ErrorCode.RECEIVER_NOT_FOUND);
    }

    @Test
    void sameAccount() {
        assertFails(check(SOURCE, SOURCE, "1.00", "0", "0"), ErrorCode.SAME_ACCOUNT_TRANSFER);
    }

    @Test
    void ownAccountsOfTheSameCustomerAreAllowed() {
        assertThatCode(() -> validator.validate(check(SOURCE, account(12L, CUSTOMER, "0"), "1.00", "0", "0")))
                .doesNotThrowAnyException();
    }

    @Test
    void perTransferLimitBoundary() {
        assertThatCode(() -> validator.validate(check(SOURCE, RECEIVER, "50000.00", "0", "0")))
                .doesNotThrowAnyException();
        assertFails(check(SOURCE, RECEIVER, "50000.01", "0", "0"), ErrorCode.PER_TRANSFER_LIMIT_EXCEEDED);
    }

    @Test
    void balanceBoundary() {
        Account poor = account(13L, CUSTOMER, "100.00");
        assertThatCode(() -> validator.validate(check(poor, RECEIVER, "100.00", "0", "0")))
                .doesNotThrowAnyException();
        assertFails(check(poor, RECEIVER, "100.01", "0", "0"), ErrorCode.INSUFFICIENT_BALANCE);
    }

    @Test
    void dailyLimitBoundary() {
        assertThatCode(() -> validator.validate(check(SOURCE, RECEIVER, "40000.00", "60000.00", "60000.00")))
                .doesNotThrowAnyException();
        assertFails(check(SOURCE, RECEIVER, "40000.01", "60000.00", "60000.00"), ErrorCode.DAILY_LIMIT_EXCEEDED);
    }

    @Test
    void monthlyLimitBoundary() {
        assertThatCode(() -> validator.validate(check(SOURCE, RECEIVER, "10000.00", "0", "490000.00")))
                .doesNotThrowAnyException();
        assertFails(check(SOURCE, RECEIVER, "10000.01", "0", "490000.00"), ErrorCode.MONTHLY_LIMIT_EXCEEDED);
    }

    // ---- order: the first violated rule wins -------------------------------------------------

    @Test
    void ownershipBeatsEverything() {
        Account foreignAndPoor = account(14L, 99L, "0.00");
        assertFails(check(foreignAndPoor, null, "99999.00", "100000", "500000"), ErrorCode.ACCOUNT_NOT_OWNED);
    }

    @Test
    void receiverBeatsSameAccountAndLimits() {
        assertFails(check(account(15L, CUSTOMER, "0"), null, "99999.00", "100000", "500000"),
                ErrorCode.RECEIVER_NOT_FOUND);
    }

    @Test
    void sameAccountBeatsLimitsAndBalance() {
        Account poor = account(16L, CUSTOMER, "0.00");
        assertFails(check(poor, poor, "99999.00", "100000", "500000"), ErrorCode.SAME_ACCOUNT_TRANSFER);
    }

    @Test
    void perTransferBeatsBalanceDailyAndMonthly() {
        assertFails(check(account(17L, CUSTOMER, "0.00"), RECEIVER, "50000.01", "100000", "500000"),
                ErrorCode.PER_TRANSFER_LIMIT_EXCEEDED);
    }

    @Test
    void balanceBeatsDailyAndMonthly() {
        assertFails(check(account(18L, CUSTOMER, "0.00"), RECEIVER, "1.00", "100000", "500000"),
                ErrorCode.INSUFFICIENT_BALANCE);
    }

    @Test
    void dailyBeatsMonthly() {
        assertFails(check(SOURCE, RECEIVER, "1.00", "100000.00", "500000.00"), ErrorCode.DAILY_LIMIT_EXCEEDED);
    }

    @Test
    void zeroUsageMeansOnlyTheAmountCounts() {
        assertThatCode(() -> validator.validate(check(SOURCE, RECEIVER, "50000.00", "50000.00", "0")))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(check(SOURCE, RECEIVER, "1.00", ZERO.toString(), "499999.00")))
                .doesNotThrowAnyException();
    }
}
