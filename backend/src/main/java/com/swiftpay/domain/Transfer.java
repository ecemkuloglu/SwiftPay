package com.swiftpay.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "transfer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "source_account_id", nullable = false, updatable = false)
    private Long sourceAccountId;

    /** Null when the receiver IBAN does not exist (stored as FAILED / RECEIVER_NOT_FOUND). */
    @Column(name = "receiver_account_id", updatable = false)
    private Long receiverAccountId;

    @Column(name = "receiver_iban", nullable = false, length = 26, updatable = false)
    private String receiverIban;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "description", length = 140, updatable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TransferStatus status;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "idempotency_key", nullable = false, length = 64, updatable = false)
    private String idempotencyKey;

    /** SHA-256 hex of the request payload; CHAR(64) in the DB. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public static Transfer createPending(Long customerId, Long sourceAccountId, Long receiverAccountId,
                                         String receiverIban, BigDecimal amount, String description,
                                         String idempotencyKey, String requestHash, Instant createdAt) {
        Transfer transfer = new Transfer();
        transfer.customerId = customerId;
        transfer.sourceAccountId = sourceAccountId;
        transfer.receiverAccountId = receiverAccountId;
        transfer.receiverIban = receiverIban;
        transfer.amount = Money.requirePositive(amount);
        transfer.currency = Money.CURRENCY;
        transfer.description = description;
        transfer.idempotencyKey = idempotencyKey;
        transfer.requestHash = requestHash;
        transfer.createdAt = createdAt;
        transfer.status = TransferStatus.PENDING;
        return transfer;
    }

    /** PENDING -> COMPLETED. */
    public void complete(Instant now) {
        requirePending(TransferStatus.COMPLETED);
        if (now == null) {
            throw new IllegalArgumentException("completedAt must not be null");
        }
        status = TransferStatus.COMPLETED;
        completedAt = now;
    }

    /** PENDING -> FAILED with the business error code as reason. */
    public void fail(String failureCode) {
        requirePending(TransferStatus.FAILED);
        if (failureCode == null || failureCode.isBlank()) {
            throw new IllegalArgumentException("failureCode must not be blank");
        }
        status = TransferStatus.FAILED;
        this.failureCode = failureCode;
    }

    private void requirePending(TransferStatus target) {
        if (status != TransferStatus.PENDING) {
            throw new IllegalStateException("Illegal transfer transition " + status + " -> " + target);
        }
    }
}
