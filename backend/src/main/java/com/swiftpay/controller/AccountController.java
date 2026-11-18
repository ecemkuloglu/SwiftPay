package com.swiftpay.controller;

import com.swiftpay.dto.AccountResponse;
import com.swiftpay.dto.ApiResponse;
import com.swiftpay.dto.LimitsResponse;
import com.swiftpay.exception.ApiResponseFactory;
import com.swiftpay.security.AuthenticatedCustomer;
import com.swiftpay.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Accounts")
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final ApiResponseFactory responses;

    @Operation(summary = "List the caller's accounts")
    @GetMapping
    public ApiResponse<List<AccountResponse>> list(@AuthenticationPrincipal AuthenticatedCustomer customer) {
        return responses.success(accountService.listAccounts(customer.customerId()));
    }

    @Operation(summary = "Per-transfer limit and today's / this month's usage (COMPLETED transfers only)")
    @GetMapping("/limits")
    public ApiResponse<LimitsResponse> limits(@AuthenticationPrincipal AuthenticatedCustomer customer) {
        return responses.success(accountService.getLimits(customer.customerId()));
    }
}
