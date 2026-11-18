package com.swiftpay.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swiftpay.exception.ErrorCode;
import com.swiftpay.exception.InsufficientBalanceException;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AccountTest {

    private static final Instant CREATED = Instant.parse("2026-05-01T00:00:00Z");
    private static final Instant LATER = Instant.parse("2026-05-14T09:30:00Z");

    private static Account accountWith(String balance) {
        return new Account(1L, "TR620006100000000000001001", new BigDecimal(balance), CREATED);
    }

    @Test
    void newAccountIsTryWithScaleTwo() {
        Account account = accountWith("100");

        assertThat(account.getCurrency()).isEqualTo("TRY");
        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
        assertThat(account.getBalance().scale()).isEqualTo(2);
        assertThat(account.getUpdatedAt()).isEqualTo(CREATED);
    }

    @Test
    void negativeOpeningBalanceIsRejected() {
        assertThatThrownBy(() -> accountWith("-0.01")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void debitOfExactBalanceLeavesZero() {
        Account account = accountWith("100.00");

        account.debit(new BigDecimal("100.00"), LATER);

        assertThat(account.getBalance()).isEqualByComparingTo("0.00");
        assertThat(account.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    void debitOfOneCentMoreThanBalanceThrowsAndChangesNothing() {
        Account account = accountWith("100.00");

        assertThatThrownBy(() -> account.debit(new BigDecimal("100.01"), LATER))
                .isInstanceOf(InsufficientBalanceException.class)
                .satisfies(e -> assertThat(((InsufficientBalanceException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INSUFFICIENT_BALANCE));
        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
        assertThat(account.getUpdatedAt()).isEqualTo(CREATED);
    }

    @Test
    void creditAddsAmountAndUpdatesTimestamp() {
        Account account = accountWith("10.50");

        account.credit(new BigDecimal("0.50"), LATER);

        assertThat(account.getBalance()).isEqualByComparingTo("11.00");
        assertThat(account.getUpdatedAt()).isEqualTo(LATER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-1", "-0.01"})
    void nonPositiveAmountsAreRejected(String amount) {
        Account account = accountWith("100.00");

        assertThatThrownBy(() -> account.debit(new BigDecimal(amount), LATER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.credit(new BigDecimal(amount), LATER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void nullAmountIsRejected() {
        Account account = accountWith("100.00");

        assertThatThrownBy(() -> account.debit(null, LATER)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void moreThanTwoDecimalsIsRejected() {
        Account account = accountWith("100.00");

        assertThatThrownBy(() -> account.debit(new BigDecimal("0.001"), LATER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.credit(new BigDecimal("1.005"), LATER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void differentScalesOfTheSameValueAreTreatedEqually() {
        Account account = accountWith("100.00");

        account.debit(new BigDecimal("1.5"), LATER);      // scale 1
        account.debit(new BigDecimal("1.500"), LATER);    // scale 3 but only trailing zeros

        assertThat(account.getBalance()).isEqualByComparingTo("97.00");
        assertThat(account.getBalance().scale()).isEqualTo(2);
    }
}
