package com.swiftpay.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.swiftpay.domain.Account;
import com.swiftpay.domain.Customer;
import com.swiftpay.support.ApiTestBase;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

/** Logs must never contain passwords, tokens or full IBANs (CLAUDE.md rule 9). */
@ExtendWith(OutputCaptureExtension.class)
class LogHygieneApiTest extends ApiTestBase {

    private static final String PASSWORD = "Very-Secret-Pa55";

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void loginTransferAndFailuresLeakNoSecrets(CapturedOutput output) throws Exception {
        Customer customer = customerRepository.save(new Customer(TEST_PREFIX + "logs-" + System.nanoTime(),
                passwordEncoder.encode(PASSWORD), "Log Test", Instant.EPOCH));
        Account source = account(customer, "1000.00");
        Account receiver = account(customer(), "0");

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"wrong-%s\"}".formatted(customer.getUsername(), PASSWORD)));
        MvcResult login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(customer.getUsername(), PASSWORD)))
                .andReturn();
        String token = json(login).at("/data/accessToken").asText();

        mockMvc.perform(post("/api/transfers").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("Idempotency-Key", "key-logs-0001").contentType(MediaType.APPLICATION_JSON)
                .content(body(source, receiver, "10.00")));
        mockMvc.perform(post("/api/transfers").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("Idempotency-Key", "key-logs-0002").contentType(MediaType.APPLICATION_JSON)
                .content(body(source, receiver, "5000.00")));                       // FAILED: insufficient

        assertThat(token).isNotBlank();
        assertThat(output).contains("Login failed", "completed", "failed");   // logging really happened
        assertThat(output).doesNotContain(PASSWORD, token, receiver.getIban(), source.getIban());
        assertThat(output).contains(receiver.getIban().substring(0, 4) + " **** **** **** **** "
                + receiver.getIban().substring(22));
        assertThat(jdbc.queryForList("SELECT details FROM audit_log WHERE customer_id = ?", String.class,
                customer.getId())).noneMatch(d -> d.contains(receiver.getIban()) || d.contains(PASSWORD));
    }
}
