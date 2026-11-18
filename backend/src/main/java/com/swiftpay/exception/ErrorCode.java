package com.swiftpay.exception;

/** All API error codes (see API_SPEC.md) with their HTTP status and a safe default message. */
public enum ErrorCode {

    VALIDATION_ERROR(400, "Request validation failed."),
    IDEMPOTENCY_KEY_REQUIRED(400, "Idempotency-Key header is required."),
    INVALID_DATE_RANGE(400, "startDate must not be after endDate."),

    UNAUTHORIZED(401, "Authentication is required."),
    INVALID_CREDENTIALS(401, "Invalid username or password."),

    ACCOUNT_NOT_OWNED(403, "Source account does not belong to the caller."),

    TRANSFER_NOT_FOUND(404, "Transfer not found."),
    RECEIVER_NOT_FOUND(404, "Receiver account not found."),
    NOT_FOUND(404, "Resource not found."),

    METHOD_NOT_ALLOWED(405, "HTTP method not allowed for this resource."),

    IDEMPOTENCY_KEY_CONFLICT(409, "Idempotency-Key was already used with a different request."),
    REQUEST_IN_PROGRESS(409, "A request with this Idempotency-Key is still being processed."),

    INSUFFICIENT_BALANCE(422, "Insufficient balance."),
    PER_TRANSFER_LIMIT_EXCEEDED(422, "Per-transfer limit exceeded."),
    DAILY_LIMIT_EXCEEDED(422, "Daily transfer limit exceeded."),
    MONTHLY_LIMIT_EXCEEDED(422, "Monthly transfer limit exceeded."),
    SAME_ACCOUNT_TRANSFER(422, "Source and receiver accounts must be different."),

    TOO_MANY_REQUESTS(429, "Too many requests."),
    TOO_MANY_ATTEMPTS(429, "Too many login attempts."),

    INTERNAL_ERROR(500, "An unexpected error occurred.");

    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
