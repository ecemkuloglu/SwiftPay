package com.swiftpay.service;

import com.swiftpay.config.AppProperties;
import com.swiftpay.domain.Account;
import com.swiftpay.domain.Transfer;
import com.swiftpay.dto.PageResponse;
import com.swiftpay.dto.TransferResponse;
import com.swiftpay.dto.TransferSummary;
import com.swiftpay.exception.BusinessException;
import com.swiftpay.exception.ErrorCode;
import com.swiftpay.repository.AccountRepository;
import com.swiftpay.repository.TransferRepository;
import com.swiftpay.repository.TransferSpecifications;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only transfer queries, always scoped to the authenticated customer. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransferQueryService {

    static final int MAX_PAGE_SIZE = 100;

    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final AppProperties appProperties;

    /** Missing and not-owned look the same (404) so ids of other customers are not revealed. */
    public TransferResponse getById(Long customerId, Long transferId) {
        Transfer transfer = transferRepository.findByIdAndCustomerId(transferId, customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSFER_NOT_FOUND));
        String sourceIban = accountRepository.findById(transfer.getSourceAccountId()).map(Account::getIban)
                .orElse(null);
        return TransferMapper.toResponse(transfer, sourceIban);
    }

    /**
     * Newest first. {@code startDate}/{@code endDate} are inclusive calendar days in the business time zone,
     * turned into [start 00:00, (end + 1 day) 00:00). Page size above 100 is capped to 100.
     */
    public PageResponse<TransferSummary> list(Long customerId, LocalDate startDate, LocalDate endDate,
                                              int page, int size) {
        if (page < 0 || size < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "page must be >= 0 and size >= 1.");
        }
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE);
        }
        ZoneId zone = appProperties.timezone();
        Instant startInclusive = startDate == null ? null : startDate.atStartOfDay(zone).toInstant();
        Instant endExclusive = endDate == null ? null : endDate.plusDays(1).atStartOfDay(zone).toInstant();
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        return PageResponse.from(transferRepository
                .findAll(TransferSpecifications.forCustomer(customerId, startInclusive, endExclusive), pageRequest)
                .map(TransferMapper::toSummary));
    }
}
