package com.swiftpay.dto;

import java.time.Instant;

/** Success envelope: {@code {data, traceId, timestamp}}. */
public record ApiResponse<T>(T data, String traceId, Instant timestamp) {
}
