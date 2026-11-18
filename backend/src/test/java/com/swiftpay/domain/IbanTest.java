package com.swiftpay.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftpay.support.TestIbans;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IbanTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "TR330006100519786457841326",   // API_SPEC example
            "TR620006100000000000001001",   // seed
            "TR690006200000000000002001"})  // seed
    void validIbans(String iban) {
        assertThat(Iban.isValid(iban)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "TR640006200000000000000002",   // API_SPEC example with a wrong checksum
            "TR330006100519786457841327",   // last digit changed
            "TR33000610051978645784132",    // 23 digits
            "TR3300061005197864578413261",  // 25 digits
            "DE89370400440532013000",       // other country
            "TR33000610051978645784132A",   // letter in BBAN
            "tr330006100519786457841326"})  // not normalised
    void invalidIbans(String iban) {
        assertThat(Iban.isValid(iban)).isFalse();
    }

    @Test
    void normalizeRemovesWhitespaceAndUpperCases() {
        assertThat(Iban.normalize(" tr33 0006 1005 1978 6457 8413 26 ")).isEqualTo("TR330006100519786457841326");
        assertThat(Iban.normalize(null)).isNull();
    }

    @Test
    void maskKeepsCountryCheckDigitsAndLastFour() {
        assertThat(Iban.mask("TR330006100519786457841326")).isEqualTo("TR33 **** **** **** **** 1326");
    }

    @Test
    void maskNeverLeaksShortOrNullInput() {
        assertThat(Iban.mask(null)).isEqualTo("****");
        assertThat(Iban.mask("TR33")).isEqualTo("****");
    }

    @Test
    void generatedTestIbansAreValid() {
        for (int i = 0; i < 50; i++) {
            assertThat(Iban.isValid(TestIbans.next())).isTrue();
        }
    }
}
