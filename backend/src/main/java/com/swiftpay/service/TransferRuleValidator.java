package com.swiftpay.service;

import com.swiftpay.config.TransferLimitsProperties;
import com.swiftpay.domain.Account;
import com.swiftpay.exception.BusinessException;
import com.swiftpay.exception.ErrorCode;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Business rules for a transfer, checked in a fixed order; the first violation wins:
 * ownership -> receiver exists -> not the same account -> per-transfer limit -> balance -> daily -> monthly.
 * Pure logic (no DB, no transaction) so the order is easy to unit test.
 */
@Component
@RequiredArgsConstructor
public class TransferRuleValidator {

    private final TransferLimitsProperties limits;

    /**
     * @param source    locked source account (null if missing)
     * @param receiver  locked receiver account (null if the IBAN does not exist in the system)
     */
    public record TransferCheck(Long customerId, Account source, Account receiver, BigDecimal amount,
                                BigDecimal usedToday, BigDecimal usedThisMonth) {
    }

    public void validate(TransferCheck check) {
        if (check.source() == null || !check.source().getCustomerId().equals(check.customerId())) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_OWNED);
        }
        if (check.receiver() == null) {
            throw new BusinessException(ErrorCode.RECEIVER_NOT_FOUND);
        }
        if (check.source().getId().equals(check.receiver().getId())) {
            throw new BusinessException(ErrorCode.SAME_ACCOUNT_TRANSFER);
        }
        if (check.amount().compareTo(limits.perTransfer()) > 0) {
            throw new BusinessException(ErrorCode.PER_TRANSFER_LIMIT_EXCEEDED);
        }
        if (check.source().getBalance().compareTo(check.amount()) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
        }
        if (!LimitService.withinLimit(check.usedToday(), check.amount(), limits.daily())) {
            throw new BusinessException(ErrorCode.DAILY_LIMIT_EXCEEDED);
        }
        if (!LimitService.withinLimit(check.usedThisMonth(), check.amount(), limits.monthly())) {
            throw new BusinessException(ErrorCode.MONTHLY_LIMIT_EXCEEDED);
        }
    }
}
