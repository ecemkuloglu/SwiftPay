package com.swiftpay.service;

import com.swiftpay.domain.Iban;
import com.swiftpay.domain.Money;
import com.swiftpay.dto.CreateTransferRequest;
import com.swiftpay.exception.BusinessException;
import com.swiftpay.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Normalises a create-transfer request and computes its SHA-256 fingerprint for idempotency.
 * "1500" and "1500.00", " tr33 ..." and "TR33...", "Kira " and "Kira" produce the same hash.
 */
@Component
public class RequestHasher {

    static final int MAX_DESCRIPTION_LENGTH = 140;

    /** Validates and canonicalises the request; any problem is a VALIDATION_ERROR (no DB access needed). */
    public NormalizedTransferRequest normalize(CreateTransferRequest request) {
        if (request == null || request.sourceAccountId() == null) {
            throw validation("sourceAccountId is required.");
        }
        String iban = Iban.normalize(request.receiverIban());
        if (!Iban.isValid(iban)) {
            throw validation("receiverIban is not a valid TR IBAN.");
        }
        String description = request.description() == null ? null : request.description().trim();
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            throw validation("description must be at most 140 characters.");
        }
        try {
            return new NormalizedTransferRequest(request.sourceAccountId(), iban,
                    Money.requirePositive(request.amount()), description == null || description.isEmpty() ? null : description);
        } catch (IllegalArgumentException e) {
            throw validation("amount must be greater than 0 with at most 2 decimals.");
        }
    }

    /** SHA-256 hex of {@code sourceAccountId|receiverIban|amount(scale 2)|description("" if null)}. */
    public String hash(NormalizedTransferRequest request) {
        String canonical = request.sourceAccountId() + "|" + request.receiverIban() + "|"
                + request.amount().toPlainString() + "|" + (request.description() == null ? "" : request.description());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static BusinessException validation(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }
}
