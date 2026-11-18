package com.swiftpay.controller;

import com.swiftpay.dto.ApiResponse;
import com.swiftpay.dto.MonthlyStatResponse;
import com.swiftpay.exception.ApiResponseFactory;
import com.swiftpay.security.AuthenticatedCustomer;
import com.swiftpay.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Statistics")
@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;
    private final ApiResponseFactory responses;

    @Operation(summary = "Last 6 months incl. the current one (Europe/Istanbul), newest first, COMPLETED only")
    @GetMapping("/monthly")
    public ApiResponse<List<MonthlyStatResponse>> monthly(@AuthenticationPrincipal AuthenticatedCustomer customer) {
        return responses.success(statisticsService.monthly(customer.customerId()));
    }
}
