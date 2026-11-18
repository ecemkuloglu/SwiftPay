package com.swiftpay.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swiftpay.domain.Account;
import com.swiftpay.domain.Customer;
import com.swiftpay.support.RepositoryTestBase;
import com.swiftpay.support.SqlCapture;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AccountRepositoryTest extends RepositoryTestBase {

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void lockByIdsOrderedReturnsAccountsInAscendingIdOrderRegardlessOfInput() {
        Customer customer = newCustomer();
        Account a = newAccount(customer, "10.00");
        Account b = newAccount(customer, "20.00");
        Account c = newAccount(customer, "30.00");
        flushAndClear();

        List<Account> locked = accountRepository.lockByIdsOrdered(List.of(c.getId(), a.getId(), b.getId()));

        assertThat(locked).extracting(Account::getId).containsExactly(a.getId(), b.getId(), c.getId());
    }

    @Test
    void lockByIdsOrderedIssuesSelectForUpdateWithOrderById() {
        Customer customer = newCustomer();
        Account a = newAccount(customer, "10.00");
        Account b = newAccount(customer, "20.00");
        flushAndClear();
        SqlCapture.clear();

        accountRepository.lockByIdsOrdered(List.of(b.getId(), a.getId()));

        String sql = singleSelect();
        System.out.println("LOCK SQL [AccountRepository.lockByIdsOrdered]: " + sql);
        // Hibernate's PostgreSQL dialect maps PESSIMISTIC_WRITE to FOR NO KEY UPDATE: an exclusive row lock
        // against other writers/lockers that does not block FK checks (FOR KEY SHARE) from transfer inserts.
        assertThat(sql).matches("(?is).*\\border by \\w+\\.id( asc)? for no key update$");
    }

    @Test
    void findIdByIbanReturnsIdOrEmpty() {
        Account account = newAccount(newCustomer(), "1.00");
        flushAndClear();

        assertThat(accountRepository.findIdByIban(account.getIban())).contains(account.getId());
        assertThat(accountRepository.findIdByIban(nextIban())).isEmpty();
    }

    @Test
    void findAllByCustomerIdReturnsOnlyThatCustomersAccounts() {
        Customer owner = newCustomer();
        Account first = newAccount(owner, "1.00");
        Account second = newAccount(owner, "2.00");
        newAccount(newCustomer(), "3.00");
        flushAndClear();

        assertThat(accountRepository.findAllByCustomerId(owner.getId()))
                .extracting(Account::getId).containsExactly(first.getId(), second.getId());
    }

    @Test
    void balanceAndUpdatedAtArePersistedWithScaleTwo() {
        Account account = newAccount(newCustomer(), "100.00");
        account.debit(new BigDecimal("0.10"), T0.plusSeconds(60));
        flushAndClear();

        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(reloaded.getBalance()).isEqualTo(new BigDecimal("99.90"));
        assertThat(reloaded.getUpdatedAt()).isEqualTo(T0.plusSeconds(60));
        assertThat(reloaded.getCurrency()).isEqualTo("TRY");
    }

    @Test
    void databaseRejectsNegativeBalance() {
        Account account = newAccount(newCustomer(), "1.00");
        em.flush();

        assertThatThrownBy(() -> em.createNativeQuery("UPDATE account SET balance = -0.01 WHERE id = :id")
                .setParameter("id", account.getId())
                .executeUpdate())
                .hasStackTraceContaining("ck_account_balance_non_negative");
    }

    @Test
    void databaseRejectsMalformedIban() {
        Customer customer = newCustomer();
        Account malformed = new Account(customer.getId(), "TR12ABC", new BigDecimal("1.00"), T0);

        // IDENTITY ids: persist executes the INSERT immediately
        assertThatThrownBy(() -> {
            em.persist(malformed);
            em.flush();
        }).hasStackTraceContaining("ck_account_iban_format");
    }

    private static String singleSelect() {
        List<String> selects = SqlCapture.statements().stream()
                .filter(s -> s.trim().toLowerCase().startsWith("select"))
                .toList();
        assertThat(selects).hasSize(1);
        return selects.get(0);
    }
}
