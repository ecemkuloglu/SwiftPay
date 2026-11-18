package com.swiftpay.support;

import com.swiftpay.domain.Account;
import com.swiftpay.domain.Customer;
import com.swiftpay.domain.Transfer;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * Repository tests on real PostgreSQL. Each test runs in a transaction that is rolled back, and builds
 * its own fixtures, so tests never depend on each other's data or on the dev seed.
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
        + "com.swiftpay.support.SqlCapture")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class RepositoryTestBase extends PostgresContainerSupport {

    protected static final Instant T0 = Instant.parse("2026-05-14T09:30:00Z");
    private static final AtomicLong IBAN_SEQ = new AtomicLong(1);

    @Autowired
    protected EntityManager em;

    protected Customer newCustomer() {
        Customer customer = new Customer("user-" + UUID.randomUUID().toString().substring(0, 8),
                "$2a$10$hash", "Test User", T0);
        em.persist(customer);
        return customer;
    }

    protected Account newAccount(Customer owner, String balance) {
        Account account = new Account(owner.getId(), nextIban(), new BigDecimal(balance), T0);
        em.persist(account);
        return account;
    }

    /** Format-valid IBAN (TR + 24 digits); the DB only checks the format. */
    protected static String nextIban() {
        return "TR" + String.format("%024d", IBAN_SEQ.getAndIncrement());
    }

    protected Transfer completedTransfer(Account source, Account receiver, String amount, Instant completedAt) {
        Transfer transfer = pendingTransfer(source, receiver, amount, completedAt);
        transfer.complete(completedAt);
        em.persist(transfer);
        return transfer;
    }

    protected Transfer failedTransfer(Account source, Account receiver, String amount, Instant createdAt) {
        Transfer transfer = pendingTransfer(source, receiver, amount, createdAt);
        transfer.fail("INSUFFICIENT_BALANCE");
        em.persist(transfer);
        return transfer;
    }

    protected Transfer pendingTransfer(Account source, Account receiver, String amount, Instant createdAt) {
        return Transfer.createPending(source.getCustomerId(), source.getId(), receiver.getId(), receiver.getIban(),
                new BigDecimal(amount), "test", UUID.randomUUID().toString(), "a".repeat(64), createdAt);
    }

    protected void flushAndClear() {
        em.flush();
        em.clear();
    }
}
