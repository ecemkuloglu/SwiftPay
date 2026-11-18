package com.swiftpay.config;

import com.swiftpay.service.TraceIdProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * First filter of every request (registered at HIGHEST_PRECEDENCE, before Spring Security): creates a new trace
 * id, puts it in MDC (all log lines), the {@code X-Trace-Id} response header and a request attribute (read by the
 * /error controller after MDC is cleared). A client-sent X-Trace-Id is ignored on purpose.
 */
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Trace-Id";
    public static final String ATTRIBUTE = TraceIdFilter.class.getName() + ".traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        MDC.put(TraceIdProvider.MDC_KEY, traceId);
        request.setAttribute(ATTRIBUTE, traceId);
        response.setHeader(HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TraceIdProvider.MDC_KEY);
        }
    }
}
