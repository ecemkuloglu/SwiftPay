package com.swiftpay.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftpay.domain.AuditLog;
import com.swiftpay.domain.Customer;
import com.swiftpay.support.RepositoryTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AuditLogRepositoryTest extends RepositoryTestBase {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void saveAndReload() {
        Customer customer = newCustomer();
        AuditLog saved = auditLogRepository.save(
                new AuditLog(customer.getId(), null, "LOGIN_FAILED", "7f3c1a9e4b2d", "attempt 1", T0));
        flushAndClear();

        AuditLog reloaded = auditLogRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getCustomerId()).isEqualTo(customer.getId());
        assertThat(reloaded.getTransferId()).isNull();
        assertThat(reloaded.getEventType()).isEqualTo("LOGIN_FAILED");
        assertThat(reloaded.getTraceId()).isEqualTo("7f3c1a9e4b2d");
        assertThat(reloaded.getDetails()).isEqualTo("attempt 1");
        assertThat(reloaded.getCreatedAt()).isEqualTo(T0);
    }
}
