package com.swiftpay.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Append-only audit record. {@code details} must never contain full IBANs, tokens or passwords. */
@Entity
@Table(name = "audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", updatable = false)
    private Long customerId;

    @Column(name = "transfer_id", updatable = false)
    private Long transferId;

    @Column(name = "event_type", nullable = false, length = 40, updatable = false)
    private String eventType;

    @Column(name = "trace_id", nullable = false, length = 64, updatable = false)
    private String traceId;

    @Column(name = "details", length = 500, updatable = false)
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AuditLog(Long customerId, Long transferId, String eventType, String traceId, String details,
                    Instant createdAt) {
        this.customerId = customerId;
        this.transferId = transferId;
        this.eventType = eventType;
        this.traceId = traceId;
        this.details = details;
        this.createdAt = createdAt;
    }
}
