package com.swiftpay.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.swiftpay.dto.ErrorResponse;
import com.swiftpay.dto.ErrorResponse.FieldError;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * The single place that turns exceptions into {@link ErrorResponse}s. Messages are always our own text:
 * exception messages, class names and stack traces never reach the client (500s are logged with the trace id).
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String INVALID_VALUE = "has an invalid value";

    private final ApiResponseFactory responses;

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ErrorResponse> business(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity.status(code.httpStatus()).body(responses.error(code, e.getMessage(), List.of()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ErrorResponse> rateLimited(RateLimitExceededException e) {
        return ResponseEntity.status(e.getErrorCode().httpStatus())
                .header(HttpHeaders.RETRY_AFTER, Long.toString(e.getRetryAfterSeconds()))
                .body(responses.error(e.getErrorCode()));
    }

    @ExceptionHandler(RequestValidationException.class)
    ResponseEntity<ErrorResponse> requestValidation(RequestValidationException e) {
        return validation(e.getFieldErrors());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> beanValidation(MethodArgumentNotValidException e) {
        return validation(e.getBindingResult().getFieldErrors().stream()
                .map(f -> new FieldError(f.getField(), f.getDefaultMessage()))
                .toList());
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErrorResponse> methodValidation(HandlerMethodValidationException e) {
        return validation(e.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(err -> new FieldError(r.getMethodParameter().getParameterName(), err.getDefaultMessage())))
                .toList());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException e) {
        if (e.getCause() instanceof UnrecognizedPropertyException unknown) {
            return validation(List.of(new FieldError(unknown.getPropertyName(), "is not a known field")));
        }
        if (e.getCause() instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
            String field = mapping.getPath().stream()
                    .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                    .collect(Collectors.joining("."));
            return validation(List.of(new FieldError(field, INVALID_VALUE)));
        }
        return ResponseEntity.badRequest()
                .body(responses.error(ErrorCode.VALIDATION_ERROR, "Malformed request body.", List.of()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> typeMismatch(MethodArgumentTypeMismatchException e) {
        return validation(List.of(new FieldError(e.getName(), INVALID_VALUE)));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ErrorResponse> missingParameter(MissingServletRequestParameterException e) {
        return validation(List.of(new FieldError(e.getParameterName(), "is required")));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ErrorResponse> missingHeader(MissingRequestHeaderException e) {
        if (IDEMPOTENCY_KEY_HEADER.equalsIgnoreCase(e.getHeaderName())) {
            return ResponseEntity.badRequest().body(responses.error(ErrorCode.IDEMPOTENCY_KEY_REQUIRED));
        }
        return validation(List.of(new FieldError(e.getHeaderName(), "header is required")));
    }

    @ExceptionHandler({HttpMediaTypeNotSupportedException.class, HttpMediaTypeNotAcceptableException.class})
    ResponseEntity<ErrorResponse> mediaType(Exception e) {
        return ResponseEntity.badRequest().body(responses.error(ErrorCode.VALIDATION_ERROR,
                "Unsupported media type; use Content-Type and Accept application/json.", List.of()));
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    ResponseEntity<ErrorResponse> notFound(Exception e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(responses.error(ErrorCode.NOT_FOUND));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> methodNotAllowed(HttpRequestMethodNotSupportedException e) {
        HttpHeaders headers = new HttpHeaders();
        if (e.getSupportedHttpMethods() != null) {
            headers.setAllow(e.getSupportedHttpMethods());
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).headers(headers)
                .body(responses.error(ErrorCode.METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responses.error(ErrorCode.INTERNAL_ERROR));
    }

    private ResponseEntity<ErrorResponse> validation(List<FieldError> fieldErrors) {
        return ResponseEntity.badRequest().body(responses.error(ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.defaultMessage(), fieldErrors));
    }
}
