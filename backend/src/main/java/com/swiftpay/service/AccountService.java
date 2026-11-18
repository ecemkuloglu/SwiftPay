package com.swiftpay.service;

import com.swiftpay.domain.Account;
import com.swiftpay.domain.Iban;
import com.swiftpay.domain.Money;
import com.swiftpay.dto.AccountResponse;
import com.swiftpay.dto.LimitsResponse;
import com.swiftpay.repository.AccountRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only account queries, always scoped to the authenticated customer. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository accountRepository;
    private final LimitService limitService;

    public List<AccountResponse> listAccounts(Long customerId) {
        return accountRepository.findAllByCustomerId(customerId).stream().map(AccountService::toResponse).toList();
    }

    public LimitsResponse getLimits(Long customerId) {
        return limitService.getLimitsSnapshot(customerId);
    }

    private static AccountResponse toResponse(Account account) {
        return new AccountResponse(account.getId(), account.getIban(), Iban.mask(account.getIban()),
                account.getCurrency(), Money.normalize(account.getBalance()));
    }
}
