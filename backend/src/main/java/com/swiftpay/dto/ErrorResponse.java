package com.swiftpay.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/** Error envelope: {@code {code, message, traceId, timestamp, fieldErrors?}}; fieldErrors only for VALIDATION_ERROR. */
public record ErrorResponse(
        String code,
        String message,
        String traceId,
        Instant timestamp,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {
    }
}
