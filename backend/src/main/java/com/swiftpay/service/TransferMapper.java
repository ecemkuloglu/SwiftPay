package com.swiftpay.service;

import com.swiftpay.domain.Iban;
import com.swiftpay.domain.Money;
import com.swiftpay.domain.Transfer;
import com.swiftpay.domain.TransferStatus;
import com.swiftpay.dto.TransferListItem;
import com.swiftpay.dto.TransferResponse;
import com.swiftpay.exception.ErrorCode;
import java.util.Arrays;

/** Transfer entity -> API DTOs; entities never leave the service layer. */
public final class TransferMapper {

    private TransferMapper() {
    }

    /** POST /transfers body: exactly the fields of API_SPEC.md (no source IBAN, no failure reason). */
    public static TransferResponse toCreated(Transfer transfer) {
        return map(transfer, null, null);
    }

    /** GET /transfers/{id}: adds the masked source IBAN and, for FAILED, a human-readable failure reason. */
    public static TransferResponse toDetail(Transfer transfer, String sourceIban) {
        String failureReason = transfer.getStatus() == TransferStatus.FAILED
                ? failureReason(transfer.getFailureCode()) : null;
        return map(transfer, Iban.mask(sourceIban), failureReason);
    }

    public static TransferListItem toListItem(Transfer transfer) {
        return new TransferListItem(
                transfer.getId(),
                Iban.mask(transfer.getReceiverIban()),
                Money.normalize(transfer.getAmount()),
                transfer.getStatus().name(),
                transfer.getCreatedAt());
    }

    /** "INSUFFICIENT_BALANCE" -> "Insufficient balance."; an unknown stored code falls back to the code itself. */
    static String failureReason(String failureCode) {
        return Arrays.stream(ErrorCode.values())
                .filter(code -> code.name().equals(failureCode))
                .map(ErrorCode::defaultMessage)
                .findFirst()
                .orElse(failureCode);
    }

    private static TransferResponse map(Transfer transfer, String sourceMaskedIban, String failureReason) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getSourceAccountId(),
                sourceMaskedIban,
                transfer.getReceiverIban(),
                Iban.mask(transfer.getReceiverIban()),
                Money.normalize(transfer.getAmount()),
                transfer.getCurrency(),
                transfer.getDescription(),
                transfer.getStatus().name(),
                transfer.getCreatedAt(),
                transfer.getCompletedAt(),
                failureReason);
    }
}
