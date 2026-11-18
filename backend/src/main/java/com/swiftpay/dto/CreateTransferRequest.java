package com.swiftpay.dto;

import java.math.BigDecimal;

/** POST /api/transfers body. Field validation annotations are added with the controller. */
public record CreateTransferRequest(Long sourceAccountId, String receiverIban, BigDecimal amount,
                                    String description) {
}
