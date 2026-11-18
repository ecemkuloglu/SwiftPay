package com.swiftpay.service;

import com.swiftpay.domain.Iban;
import com.swiftpay.domain.Money;
import com.swiftpay.domain.Transfer;
import com.swiftpay.domain.TransferStatus;
import com.swiftpay.dto.TransferResponse;
import com.swiftpay.dto.TransferSummary;

/** Entity -> DTO mapping; entities never leave the service layer. */
final class TransferMapper {

    private TransferMapper() {
    }

    static TransferResponse toResponse(Transfer transfer, String sourceIban) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getSourceAccountId(),
                Iban.mask(sourceIban),
                transfer.getReceiverIban(),
                Iban.mask(transfer.getReceiverIban()),
                Money.normalize(transfer.getAmount()),
                transfer.getCurrency(),
                transfer.getDescription(),
                transfer.getStatus().name(),
                transfer.getCreatedAt(),
                transfer.getCompletedAt(),
                transfer.getStatus() == TransferStatus.FAILED ? transfer.getFailureCode() : null);
    }

    static TransferSummary toSummary(Transfer transfer) {
        return new TransferSummary(
                transfer.getId(),
                Iban.mask(transfer.getReceiverIban()),
                Money.normalize(transfer.getAmount()),
                transfer.getStatus().name(),
                transfer.getCreatedAt());
    }
}
