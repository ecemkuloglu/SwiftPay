package com.swiftpay.exception;

import com.swiftpay.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces Spring Boot's default /error page: errors raised outside Spring MVC (servlet container, filters)
 * are returned in the same {@link ErrorResponse} shape, without internal details.
 */
@Slf4j
@Hidden
@RestController
@RequiredArgsConstructor
public class ApiErrorController implements ErrorController {

    private final ApiResponseFactory responses;

    @RequestMapping("/error")
    public ResponseEntity<ErrorResponse> error(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int httpStatus = status instanceof Integer i ? i : 500;
        ErrorCode code = switch (httpStatus) {
            case 400 -> ErrorCode.VALIDATION_ERROR;
            case 401, 403 -> ErrorCode.UNAUTHORIZED;
            case 404 -> ErrorCode.NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            default -> ErrorCode.INTERNAL_ERROR;
        };
        if (code == ErrorCode.INTERNAL_ERROR) {
            Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
            log.error("Unhandled error outside Spring MVC (status {})", httpStatus,
                    exception instanceof Throwable t ? t : null);
        }
        return ResponseEntity.status(code.httpStatus()).body(responses.error(code, request));
    }
}
