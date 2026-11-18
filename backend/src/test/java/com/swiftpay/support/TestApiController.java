package com.swiftpay.support;

import com.swiftpay.dto.ApiResponse;
import com.swiftpay.exception.ApiResponseFactory;
import com.swiftpay.security.AuthenticatedCustomer;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoints (exists only on the test classpath) used to exercise security and error handling. */
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestApiController {

    private final ApiResponseFactory responses;

    @GetMapping("/whoami")
    public ApiResponse<Map<String, Object>> whoami(@AuthenticationPrincipal AuthenticatedCustomer principal) {
        return responses.success(Map.of("customerId", principal.customerId(), "zero", new BigDecimal("0.00")));
    }

    /** Fails with an exception whose message must never reach the client. */
    @GetMapping("/boom")
    public ApiResponse<Void> boom() {
        throw new IllegalStateException("db password=hunter2 at internal.Secret");
    }

    @GetMapping("/params")
    public ApiResponse<Integer> params(@RequestParam int page, @RequestParam(defaultValue = "1") @Min(1) int size) {
        return responses.success(page + size);
    }

    @GetMapping("/header")
    public ApiResponse<String> header(@RequestHeader("Idempotency-Key") String key,
                                      @RequestHeader("X-Other") String other) {
        return responses.success(key + other);
    }
}
