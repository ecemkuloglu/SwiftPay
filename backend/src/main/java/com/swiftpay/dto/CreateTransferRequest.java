package com.swiftpay.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** POST /api/transfers body. The service normalises and re-checks it (defence in depth). */
public record CreateTransferRequest(
        @NotNull Long sourceAccountId,
        @NotBlank @ValidIban
        @Schema(example = "TR690006200000000000002001") String receiverIban,
        @NotNull @Positive @Digits(integer = 17, fraction = 2)
        @Schema(type = "string", example = "1500.00") BigDecimal amount,
        @Size(max = 140) @Schema(example = "Kira") String description) {
}
