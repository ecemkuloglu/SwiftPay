package com.swiftpay.support;

import com.swiftpay.domain.Account;
import com.swiftpay.domain.Customer;
import com.swiftpay.dto.CreateTransferRequest;
import com.swiftpay.exception.BusinessException;
import com.swiftpay.exception.ErrorCode;
import com.swiftpay.repository.AccountRepository;
import com.swiftpay.repository.CustomerRepository;
import com.swiftpay.service.TransferService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Full Spring context on real PostgreSQL with a settable clock. There is NO test-managed transaction:
 * every fixture and every service call commits, exactly like production, so locking and concurrency are real.
 * Each test creates its own customers (username prefix "it-") and only those rows are deleted afterwards;
 * the dev seed in the shared container is left alone.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
        + "com.swiftpay.support.SqlCapture")
@Import(TestClockConfig.class)
public abstract class IntegrationTestBase extends PostgresContainerSupport {

    protected static final String TEST_PREFIX = "it-";

    @Autowired
    protected TransferService transferService;
    @Autowired
    protected CustomerRepository customerRepository;
    @Autowired
    protected AccountRepository accountRepository;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected MutableClock clock;

    @BeforeEach
    void resetClock() {
        clock.set(TestClockConfig.DEFAULT_NOW);
        SqlCapture.clear();
    }

    @AfterEach
    void deleteOwnRows() {
        String own = "(SELECT id FROM customer WHERE username LIKE '" + TEST_PREFIX + "%')";
        jdbc.update("DELETE FROM audit_log WHERE customer_id IN " + own);
        jdbc.update("DELETE FROM transfer WHERE customer_id IN " + own);
        jdbc.update("DELETE FROM account WHERE customer_id IN " + own);
        jdbc.update("DELETE FROM customer WHERE id IN " + own);
    }

    // ---- fixtures (each save commits) -----------------------------------------------------

    protected Customer customer() {
        return customerRepository.save(new Customer(TEST_PREFIX + UUID.randomUUID().toString().substring(0, 12),
                "$2a$10$hash", "Integration Test", Instant.EPOCH));
    }

    protected Account account(Customer owner, String balance) {
        return accountRepository.save(new Account(owner.getId(), TestIbans.next(), new BigDecimal(balance),
                Instant.EPOCH));
    }

    protected BigDecimal balance(Account account) {
        return accountRepository.findById(account.getId()).orElseThrow().getBalance();
    }

    protected static CreateTransferRequest request(Account source, Account receiver, String amount) {
        return new CreateTransferRequest(source.getId(), receiver.getIban(), new BigDecimal(amount), "test");
    }

    protected static String newKey() {
        return UUID.randomUUID().toString();
    }

    /** Runs a transfer with a fresh key and returns the error code, or null on success. */
    protected ErrorCode transfer(Customer customer, Account source, Account receiver, String amount) {
        try {
            transferService.createTransfer(customer.getId(), newKey(), request(source, receiver, amount));
            return null;
        } catch (BusinessException e) {
            return e.getErrorCode();
        }
    }

    protected long countTransfers(Customer customer, String status) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM transfer WHERE customer_id = ? AND status = ?",
                Long.class, customer.getId(), status);
    }
}
