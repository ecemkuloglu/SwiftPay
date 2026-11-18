package com.swiftpay.service;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/** Current request's trace id from MDC (set by a request filter in a later step). */
@Component
public class TraceIdProvider {

    public static final String MDC_KEY = "traceId";
    static final String FALLBACK = "n/a";

    public String currentTraceId() {
        String traceId = MDC.get(MDC_KEY);
        return traceId == null || traceId.isBlank() ? FALLBACK : traceId;
    }
}
