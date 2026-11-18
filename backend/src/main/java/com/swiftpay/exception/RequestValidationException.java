package com.swiftpay.exception;

import com.swiftpay.dto.ErrorResponse.FieldError;
import java.util.List;

/** Thrown by controllers that validate programmatically; becomes 400 VALIDATION_ERROR with fieldErrors. */
public class RequestValidationException extends RuntimeException {

    private final transient List<FieldError> fieldErrors;

    public RequestValidationException(List<FieldError> fieldErrors) {
        super("Request validation failed");
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public List<FieldError> getFieldErrors() {
        return fieldErrors;
    }
}
