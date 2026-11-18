package com.swiftpay.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Turkish IBAN: TR + 24 digits with a valid mod-97 checksum, after removing spaces and upper-casing. */
@Documented
@Constraint(validatedBy = ValidIbanValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidIban {

    String message() default "must be a valid TR IBAN (TR + 24 digits, valid checksum)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
