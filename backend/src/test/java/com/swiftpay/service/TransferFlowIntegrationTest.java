package com.swiftpay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;

import com.swiftpay.domain.Account;
import com.swiftpay.domain.Customer;
import com.swiftpay.repository.AccountRepository;
import com.swiftpay.repository.CustomerRepository;
import com.swiftpay.repository.TransferRepository;
import com.swiftpay.support.IntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Flow checks that need spies (separate Spring context): the order of the lock/insert steps and the
 * rollback of an unexpected failure.
 */
class TransferFlowIntegrationTest extends IntegrationTestBase {

    @MockitoSpyBean
    private AuditLogger auditLogger;
    @MockitoSpyBean
    private CustomerRepository customerRepositorySpy;
    @MockitoSpyBean
    private AccountRepository accountRepositorySpy;
    @MockitoSpyBean
    private TransferRepository transferRepositorySpy;

    @Test
    void customerLockThenIdempotentInsertThenAccountLocksInIdOrder() {
        Customer customer = customer();
        Account receiver = account(customer(), "0.00");          // lower id
        Account source = account(customer, "100.00");            // higher id

        transferService.createTransfer(customer.getId(), newKey(), request(source, receiver, "1.00"));

        InOrder order = inOrder(customerRepositorySpy, accountRepositorySpy, transferRepositorySpy, auditLogger);
        order.verify(customerRepositorySpy).lockById(customer.getId());
        order.verify(accountRepositorySpy).existsByIdAndCustomerId(source.getId(), customer.getId());
        order.verify(transferRepositorySpy).insertPendingIfAbsent(eq(customer.getId()), eq(source.getId()),
                eq(receiver.getId()), anyString(), any(), any(), anyString(), anyString(), any());
        order.verify(accountRepositorySpy).lockByIdsOrdered(anyCollection());
        order.verify(auditLogger).transferCompleted(any());
        assertThat(receiver.getId()).isLessThan(source.getId());
    }

    @Test
    void lockedAccountsAreRequestedAsTheSourceAndReceiverIds() {
        Customer customer = customer();
        Account receiver = account(customer(), "0.00");
        Account source = account(customer, "100.00");

        transferService.createTransfer(customer.getId(), newKey(), request(source, receiver, "1.00"));

        org.mockito.Mockito.verify(accountRepositorySpy)
                .lockByIdsOrdered(List.of(source.getId(), receiver.getId()));  // SQL sorts them: ORDER BY id
    }

    @Test
    void unexpectedFailureAfterDebitRollsBackEverything() {
        Customer customer = customer();
        Account source = account(customer, "100.00");
        Account receiver = account(customer(), "0.00");
        doThrow(new IllegalStateException("audit store down")).when(auditLogger).transferCompleted(any());

        assertThatThrownBy(() -> transferService.createTransfer(customer.getId(), newKey(),
                request(source, receiver, "40.00"))).isInstanceOf(IllegalStateException.class);

        assertThat(balance(source)).isEqualByComparingTo("100.00");
        assertThat(balance(receiver)).isEqualByComparingTo("0.00");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transfer WHERE customer_id = ?", Long.class,
                customer.getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE customer_id = ?", Long.class,
                customer.getId())).isZero();
    }
}
