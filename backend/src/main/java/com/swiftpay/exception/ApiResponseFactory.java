package com.swiftpay.exception;

import com.swiftpay.config.TraceIdFilter;
import com.swiftpay.dto.ApiResponse;
import com.swiftpay.dto.ErrorResponse;
import com.swiftpay.service.TraceIdProvider;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Builds the success and error envelopes with the current trace id and timestamp. */
@Component
@RequiredArgsConstructor
public class ApiResponseFactory {

    private final TraceIdProvider traceIdProvider;
    private final Clock clock;

    public <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data, traceIdProvider.currentTraceId(), clock.instant());
    }

    public ErrorResponse error(ErrorCode code) {
        return error(code, code.defaultMessage(), List.of());
    }

    public ErrorResponse error(ErrorCode code, String message, List<ErrorResponse.FieldError> fieldErrors) {
        return new ErrorResponse(code.name(), message, traceIdProvider.currentTraceId(), clock.instant(), fieldErrors);
    }

    /** For code running outside the MDC scope (e.g. the /error dispatch): trace id from the request attribute. */
    public ErrorResponse error(ErrorCode code, HttpServletRequest request) {
        Object traceId = request.getAttribute(TraceIdFilter.ATTRIBUTE);
        return new ErrorResponse(code.name(), code.defaultMessage(),
                traceId != null ? traceId.toString() : traceIdProvider.currentTraceId(), clock.instant(), List.of());
    }
}
