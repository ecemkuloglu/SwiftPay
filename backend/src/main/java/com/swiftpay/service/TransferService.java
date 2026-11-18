package com.swiftpay.service;

import com.swiftpay.domain.Account;
import com.swiftpay.domain.Transfer;
import com.swiftpay.dto.CreateTransferRequest;
import com.swiftpay.dto.TransferResult;
import com.swiftpay.exception.BusinessException;
import com.swiftpay.exception.ErrorCode;
import com.swiftpay.repository.AccountRepository;
import com.swiftpay.repository.CustomerRepository;
import com.swiftpay.repository.TransferRepository;
import com.swiftpay.service.TransferRuleValidator.TransferCheck;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates transfers. The whole flow runs in ONE transaction:
 * <ol>
 *   <li>normalise + hash the request (no DB)</li>
 *   <li>lock the customer row (serialises this customer's transfers, so limit checks cannot race)</li>
 *   <li>source account must exist and belong to the caller, else ACCOUNT_NOT_OWNED without a transfer row</li>
 *   <li>INSERT ... ON CONFLICT DO NOTHING on (customer_id, idempotency_key); 0 rows -> replay rules</li>
 *   <li>lock source + receiver accounts in ascending id order</li>
 *   <li>business rules; a violation stores the transfer as FAILED and still commits (noRollbackFor)</li>
 *   <li>debit, credit, complete</li>
 * </ol>
 * Any other exception rolls everything back. Collaborators (AuditLogger, LimitService, validator) are not
 * @Transactional, so a BusinessException never crosses another transactional proxy that could mark the
 * transaction rollback-only.
 */
@Service
@RequiredArgsConstructor
public class TransferService {

    static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final RequestHasher requestHasher;
    private final LimitService limitService;
    private final TransferRuleValidator ruleValidator;
    private final AuditLogger auditLogger;
    private final Clock clock;

    @Transactional(noRollbackFor = BusinessException.class)
    public TransferResult createTransfer(Long customerId, String idempotencyKey, CreateTransferRequest request) {
        // a. normalise and fingerprint before any DB work
        String key = requireIdempotencyKey(idempotencyKey);
        NormalizedTransferRequest normalized = requestHasher.normalize(request);
        String requestHash = requestHasher.hash(normalized);

        // b. serialise all transfers of this customer
        customerRepository.lockById(customerId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        // c. source account precondition (no transfer row: its FK would point to someone else's account)
        if (!accountRepository.existsByIdAndCustomerId(normalized.sourceAccountId(), customerId)) {
            auditLogger.transferRejected(customerId, normalized.sourceAccountId(), ErrorCode.ACCOUNT_NOT_OWNED);
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_OWNED);
        }

        // d. idempotent insert; the receiver id is resolved first (no lock) so it is stored with the row
        Long receiverId = accountRepository.findIdByIban(normalized.receiverIban()).orElse(null);
        Instant now = clock.instant();
        int inserted = transferRepository.insertPendingIfAbsent(customerId, normalized.sourceAccountId(), receiverId,
                normalized.receiverIban(), normalized.amount(), normalized.description(), key, requestHash, now);
        Transfer transfer = transferRepository.findByCustomerIdAndIdempotencyKey(customerId, key)
                .orElseThrow(() -> new IllegalStateException("Transfer row missing after insert"));
        if (inserted == 0) {
            return replay(transfer, requestHash);
        }

        // e. lock both accounts, ALWAYS in ascending id order (deadlock-free)
        List<Account> locked = accountRepository.lockByIdsOrdered(
                Stream.of(normalized.sourceAccountId(), receiverId).filter(id -> id != null).distinct().toList());
        Account source = find(locked, normalized.sourceAccountId());
        Account receiver = receiverId == null ? null : find(locked, receiverId);

        // f. business rules (inside the locks); violation -> FAILED row, audit, commit, rethrow
        TransferCheck check = new TransferCheck(customerId, source, receiver, normalized.amount(),
                limitService.usedToday(customerId, now), limitService.usedThisMonth(customerId, now));
        try {
            ruleValidator.validate(check);
        } catch (BusinessException e) {
            transfer.fail(e.getErrorCode().name());
            auditLogger.transferFailed(transfer, e.getErrorCode());
            throw e;
        }

        // g. happy path
        source.debit(normalized.amount(), now);
        receiver.credit(normalized.amount(), now);
        transfer.complete(now);
        auditLogger.transferCompleted(transfer);
        return new TransferResult(TransferMapper.toCreated(transfer), false);
    }

    private TransferResult replay(Transfer existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
        return switch (existing.getStatus()) {
            case COMPLETED -> new TransferResult(TransferMapper.toCreated(existing), true);
            case FAILED -> throw new BusinessException(ErrorCode.valueOf(existing.getFailureCode()));
            case PENDING -> throw new BusinessException(ErrorCode.REQUEST_IN_PROGRESS);
        };
    }

    private static String requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        String key = idempotencyKey.trim();
        if (key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Idempotency-Key must be at most 64 characters.");
        }
        return key;
    }

    private static Account find(List<Account> accounts, Long id) {
        return accounts.stream().filter(a -> a.getId().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Locked account " + id + " not found"));
    }
}
