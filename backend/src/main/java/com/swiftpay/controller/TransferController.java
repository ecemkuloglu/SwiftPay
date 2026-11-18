package com.swiftpay.controller;

import com.swiftpay.dto.ApiResponse;
import com.swiftpay.dto.CreateTransferRequest;
import com.swiftpay.dto.ErrorResponse.FieldError;
import com.swiftpay.dto.PageResponse;
import com.swiftpay.dto.TransferListItem;
import com.swiftpay.dto.TransferResponse;
import com.swiftpay.dto.TransferResult;
import com.swiftpay.exception.ApiResponseFactory;
import com.swiftpay.exception.BusinessException;
import com.swiftpay.exception.ErrorCode;
import com.swiftpay.exception.GlobalExceptionHandler;
import com.swiftpay.exception.RequestValidationException;
import com.swiftpay.security.AuthenticatedCustomer;
import com.swiftpay.service.RateLimiter;
import com.swiftpay.service.TransferQueryService;
import com.swiftpay.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Thin controller: rate limit, validate, call the service, map the result. The customer id is never client input. */
@Tag(name = "Transfers")
@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferController {

    public static final String REPLAYED_HEADER = "Idempotent-Replayed";
    private static final String IDEMPOTENCY_KEY = GlobalExceptionHandler.IDEMPOTENCY_KEY_HEADER;
    private static final Pattern KEY_FORMAT = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");
    private static final Map<String, Sort.Direction> SORTS = Map.of(
            "createdAt,desc", Sort.Direction.DESC,
            "createdAt,asc", Sort.Direction.ASC);

    private final TransferService transferService;
    private final TransferQueryService transferQueryService;
    private final RateLimiter rateLimiter;
    private final Validator validator;
    private final ApiResponseFactory responses;

    @Operation(summary = "Create a transfer (idempotent per Idempotency-Key)",
            description = "201 for a new transfer; 200 + Idempotent-Replayed: true when the same key and payload "
                    + "were already processed. Business-rule failures are stored as FAILED and returned as errors.")
    @PostMapping
    public ResponseEntity<ApiResponse<TransferResponse>> create(
            @AuthenticationPrincipal AuthenticatedCustomer customer,
            @Parameter(in = ParameterIn.HEADER, name = IDEMPOTENCY_KEY, required = true,
                    description = "Client-generated unique key (UUID recommended)",
                    schema = @Schema(type = "string", pattern = "^[A-Za-z0-9_-]{8,64}$"))
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody CreateTransferRequest request) {
        rateLimiter.consumeTransfer(customer.customerId());
        validateIdempotencyKey(idempotencyKey);   // header before body: a missing key is reported first
        validateBody(request);

        TransferResult result = transferService.createTransfer(customer.customerId(), idempotencyKey, request);
        if (result.replayed()) {
            return ResponseEntity.ok().header(REPLAYED_HEADER, "true").body(responses.success(result.transfer()));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(responses.success(result.transfer()));
    }

    @Operation(summary = "Get one of the caller's transfers (404 if it does not exist or is not the caller's)")
    @GetMapping("/{id}")
    public ApiResponse<TransferResponse> get(@AuthenticationPrincipal AuthenticatedCustomer customer,
                                             @PathVariable Long id) {
        return responses.success(transferQueryService.getById(customer.customerId(), id));
    }

    @Operation(summary = "List the caller's transfers, optionally within [startDate, endDate] (Istanbul days)")
    @GetMapping
    public ApiResponse<PageResponse<TransferListItem>> list(
            @AuthenticationPrincipal AuthenticatedCustomer customer,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(TransferQueryService.MAX_PAGE_SIZE) int size,
            @Parameter(schema = @Schema(allowableValues = {"createdAt,desc", "createdAt,asc"}))
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Sort.Direction direction = SORTS.get(sort.replace(" ", ""));
        if (direction == null) {
            throw new RequestValidationException(List.of(
                    new FieldError("sort", "must be createdAt,desc or createdAt,asc")));
        }
        return responses.success(transferQueryService.list(customer.customerId(), startDate, endDate, page, size,
                direction));
    }

    private static void validateIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        if (!KEY_FORMAT.matcher(key).matches()) {
            throw new RequestValidationException(List.of(new FieldError(IDEMPOTENCY_KEY,
                    "must be 8 to 64 characters of A-Z, a-z, 0-9, '_' or '-'")));
        }
    }

    private void validateBody(CreateTransferRequest request) {
        List<FieldError> errors = validator.validate(request).stream()
                .map(v -> new FieldError(v.getPropertyPath().toString(), v.getMessage()))
                .sorted((a, b) -> a.field().compareTo(b.field()))
                .toList();
        if (!errors.isEmpty()) {
            throw new RequestValidationException(errors);
        }
    }
}
