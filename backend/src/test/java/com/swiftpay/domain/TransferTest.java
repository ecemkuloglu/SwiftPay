package com.swiftpay.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TransferTest {

    private static final Instant CREATED = Instant.parse("2026-05-14T09:30:00Z");
    private static final Instant DONE = Instant.parse("2026-05-14T09:30:01Z");

    private static Transfer pending() {
        return Transfer.createPending(1L, 1L, 3L, "TR690006200000000000002001", new BigDecimal("1500.00"),
                "Kira", "key-1", "a".repeat(64), CREATED);
    }

    @Test
    void createPendingSetsInitialState() {
        Transfer transfer = pending();

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.PENDING);
        assertThat(transfer.getCurrency()).isEqualTo("TRY");
        assertThat(transfer.getAmount()).isEqualByComparingTo("1500.00");
        assertThat(transfer.getCreatedAt()).isEqualTo(CREATED);
        assertThat(transfer.getCompletedAt()).isNull();
        assertThat(transfer.getFailureCode()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "0.001"})
    void createPendingRejectsInvalidAmounts(String amount) {
        BigDecimal value = new BigDecimal(amount);
        assertThatThrownBy(() -> Transfer.createPending(1L, 1L, 3L, "TR690006200000000000002001", value,
                null, "k", "a".repeat(64), CREATED)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pendingToCompleted() {
        Transfer transfer = pending();

        transfer.complete(DONE);

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(transfer.getCompletedAt()).isEqualTo(DONE);
        assertThat(transfer.getFailureCode()).isNull();
    }

    @Test
    void pendingToFailed() {
        Transfer transfer = pending();

        transfer.fail("DAILY_LIMIT_EXCEEDED");

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(transfer.getFailureCode()).isEqualTo("DAILY_LIMIT_EXCEEDED");
        assertThat(transfer.getCompletedAt()).isNull();
    }

    @Test
    void completedCannotBeCompletedAgain() {
        Transfer transfer = pending();
        transfer.complete(DONE);

        assertThatThrownBy(() -> transfer.complete(DONE)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void completedCannotFail() {
        Transfer transfer = pending();
        transfer.complete(DONE);

        assertThatThrownBy(() -> transfer.fail("INSUFFICIENT_BALANCE")).isInstanceOf(IllegalStateException.class);
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(transfer.getFailureCode()).isNull();
    }

    @Test
    void failedCannotComplete() {
        Transfer transfer = pending();
        transfer.fail("INSUFFICIENT_BALANCE");

        assertThatThrownBy(() -> transfer.complete(DONE)).isInstanceOf(IllegalStateException.class);
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(transfer.getCompletedAt()).isNull();
    }

    @Test
    void failedCannotFailAgain() {
        Transfer transfer = pending();
        transfer.fail("INSUFFICIENT_BALANCE");

        assertThatThrownBy(() -> transfer.fail("DAILY_LIMIT_EXCEEDED")).isInstanceOf(IllegalStateException.class);
        assertThat(transfer.getFailureCode()).isEqualTo("INSUFFICIENT_BALANCE");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void failRequiresAReason(String reason) {
        Transfer transfer = pending();

        assertThatThrownBy(() -> transfer.fail(reason)).isInstanceOf(IllegalArgumentException.class);
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.PENDING);
    }

    @Test
    void completeRequiresATimestamp() {
        Transfer transfer = pending();

        assertThatThrownBy(() -> transfer.complete(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.PENDING);
    }
}
