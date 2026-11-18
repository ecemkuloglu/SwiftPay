package com.swiftpay.service;

import com.swiftpay.domain.AuditEventType;
import com.swiftpay.domain.AuditLog;
import com.swiftpay.domain.Iban;
import com.swiftpay.domain.Transfer;
import com.swiftpay.exception.ErrorCode;
import com.swiftpay.repository.AuditLogRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Writes audit_log rows and the matching log line. Deliberately NOT @Transactional: it runs inside the
 * caller's transaction (TransferService), so audit rows commit or roll back together with the transfer.
 * Details never contain full IBANs, tokens or passwords.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogger {

    private final AuditLogRepository auditLogRepository;
    private final TraceIdProvider traceIdProvider;
    private final Clock clock;

    public void transferCompleted(Transfer transfer) {
        String details = "amount=" + transfer.getAmount() + " receiver=" + Iban.mask(transfer.getReceiverIban());
        save(transfer.getCustomerId(), transfer.getId(), AuditEventType.TRANSFER_COMPLETED, details);
        log.info("Transfer {} completed: {}", transfer.getId(), details);
    }

    public void transferFailed(Transfer transfer, ErrorCode reason) {
        String details = "reason=" + reason + " amount=" + transfer.getAmount()
                + " receiver=" + Iban.mask(transfer.getReceiverIban());
        save(transfer.getCustomerId(), transfer.getId(), AuditEventType.TRANSFER_FAILED, details);
        log.info("Transfer {} failed: {}", transfer.getId(), details);
    }

    public void transferRejected(Long customerId, Long sourceAccountId, ErrorCode reason) {
        String details = "reason=" + reason + " sourceAccountId=" + sourceAccountId;
        save(customerId, null, AuditEventType.TRANSFER_REJECTED, details);
        log.warn("Transfer rejected for customer {}: {}", customerId, details);
    }

    private void save(Long customerId, Long transferId, AuditEventType type, String details) {
        auditLogRepository.save(new AuditLog(customerId, transferId, type.name(), traceIdProvider.currentTraceId(),
                details, clock.instant()));
    }
}
