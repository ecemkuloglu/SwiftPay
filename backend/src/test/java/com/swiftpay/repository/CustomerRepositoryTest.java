package com.swiftpay.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftpay.domain.Customer;
import com.swiftpay.support.RepositoryTestBase;
import com.swiftpay.support.SqlCapture;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CustomerRepositoryTest extends RepositoryTestBase {

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void findByUsername() {
        Customer customer = newCustomer();
        flushAndClear();

        assertThat(customerRepository.findByUsername(customer.getUsername()))
                .get().extracting(Customer::getId).isEqualTo(customer.getId());
        assertThat(customerRepository.findByUsername("nobody")).isEmpty();
    }

    @Test
    void lockByIdIssuesSelectForUpdate() {
        Customer customer = newCustomer();
        flushAndClear();
        SqlCapture.clear();

        assertThat(customerRepository.lockById(customer.getId())).isPresent();

        List<String> selects = SqlCapture.statements().stream()
                .filter(s -> s.trim().toLowerCase().startsWith("select"))
                .toList();
        assertThat(selects).hasSize(1);
        System.out.println("LOCK SQL [CustomerRepository.lockById]: " + selects.get(0));
        assertThat(selects.get(0)).endsWithIgnoringCase("for no key update");
    }
}
