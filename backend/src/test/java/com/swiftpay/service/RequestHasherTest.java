package com.swiftpay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swiftpay.dto.CreateTransferRequest;
import com.swiftpay.exception.BusinessException;
import com.swiftpay.exception.ErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RequestHasherTest {

    private static final String IBAN = "TR690006200000000000002001";

    private final RequestHasher hasher = new RequestHasher();

    private String hashOf(Long source, String iban, String amount, String description) {
        return hasher.hash(hasher.normalize(new CreateTransferRequest(source, iban, new BigDecimal(amount), description)));
    }

    @Test
    void amountScaleDoesNotChangeTheHash() {
        assertThat(hashOf(1L, IBAN, "1500", "Kira")).isEqualTo(hashOf(1L, IBAN, "1500.00", "Kira"))
                .isEqualTo(hashOf(1L, IBAN, "1500.0", "Kira"));
    }

    @Test
    void ibanFormattingDoesNotChangeTheHash() {
        assertThat(hashOf(1L, " tr69 0006 2000 0000 0000 0020 01", "1", null)).isEqualTo(hashOf(1L, IBAN, "1", null));
    }

    @Test
    void descriptionIsTrimmedAndBlankEqualsNull() {
        assertThat(hashOf(1L, IBAN, "1", "  Kira ")).isEqualTo(hashOf(1L, IBAN, "1", "Kira"));
        assertThat(hashOf(1L, IBAN, "1", "   ")).isEqualTo(hashOf(1L, IBAN, "1", null))
                .isEqualTo(hashOf(1L, IBAN, "1", ""));
    }

    @Test
    void everyFieldChangesTheHash() {
        String base = hashOf(1L, IBAN, "1500.00", "Kira");
        assertThat(hashOf(2L, IBAN, "1500.00", "Kira")).isNotEqualTo(base);
        assertThat(hashOf(1L, "TR620006100000000000001001", "1500.00", "Kira")).isNotEqualTo(base);
        assertThat(hashOf(1L, IBAN, "1500.01", "Kira")).isNotEqualTo(base);
        assertThat(hashOf(1L, IBAN, "1500.00", "kira")).isNotEqualTo(base);
    }

    @Test
    void hashIsSha256Hex() {
        assertThat(hashOf(1L, IBAN, "1", null)).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void normalizedValuesAreCanonical() {
        NormalizedTransferRequest n = hasher.normalize(
                new CreateTransferRequest(1L, " tr69 0006 2000 0000 0000 0020 01", new BigDecimal("1500"), "  "));

        assertThat(n.receiverIban()).isEqualTo(IBAN);
        assertThat(n.amount()).isEqualTo(new BigDecimal("1500.00"));
        assertThat(n.description()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.001", "10.005"})
    void invalidAmountsAreValidationErrors(String amount) {
        assertValidationError(new CreateTransferRequest(1L, IBAN, new BigDecimal(amount), null));
    }

    @Test
    void missingFieldsAndBadValuesAreValidationErrors() {
        assertValidationError(null);
        assertValidationError(new CreateTransferRequest(null, IBAN, BigDecimal.ONE, null));
        assertValidationError(new CreateTransferRequest(1L, IBAN, null, null));
        assertValidationError(new CreateTransferRequest(1L, null, BigDecimal.ONE, null));
        assertValidationError(new CreateTransferRequest(1L, "TR640006200000000000000002", BigDecimal.ONE, null));
        assertValidationError(new CreateTransferRequest(1L, IBAN, BigDecimal.ONE, "x".repeat(141)));
    }

    @Test
    void descriptionOfExactly140CharsIsAccepted() {
        assertThat(hasher.normalize(new CreateTransferRequest(1L, IBAN, BigDecimal.ONE, "x".repeat(140)))
                .description()).hasSize(140);
    }

    private void assertValidationError(CreateTransferRequest request) {
        assertThatThrownBy(() -> hasher.normalize(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }
}
