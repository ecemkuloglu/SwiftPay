package com.swiftpay.service;

import com.swiftpay.domain.Account;
import com.swiftpay.domain.Iban;
import com.swiftpay.domain.Money;
import com.swiftpay.dto.AccountResponse;

/** Account entity -> API DTO. The full IBAN is only ever returned to the account's owner. */
public final class AccountMapper {

    private AccountMapper() {
    }

    public static AccountResponse toResponse(Account account) {
        return new AccountResponse(account.getId(), account.getIban(), Iban.mask(account.getIban()),
                account.getCurrency(), Money.normalize(account.getBalance()));
    }
}
