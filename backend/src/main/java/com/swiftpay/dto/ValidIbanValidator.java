package com.swiftpay.dto;

import com.swiftpay.domain.Iban;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Null is left to @NotBlank; anything else must normalise to a valid TR IBAN. */
public class ValidIbanValidator implements ConstraintValidator<ValidIban, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || Iban.isValid(Iban.normalize(value));
    }
}
