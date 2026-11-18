package com.swiftpay.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.swiftpay.domain.Account;
import com.swiftpay.domain.Customer;
import com.swiftpay.support.ApiTestBase;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

/** Test limits (src/test/resources/config/application.yml): 3 failed logins / 15 min, 5 transfers / min. */
class RateLimitApiTest extends ApiTestBase {

    private static final String PASSWORD = "Correct-Horse-1";

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Customer user;

    @BeforeEach
    void user() {
        user = customerRepository.save(new Customer(TEST_PREFIX + "rl-" + System.nanoTime(),
                passwordEncoder.encode(PASSWORD), "Rate Limit", Instant.EPOCH));
    }

    private MvcResult login(String password, String ip, String forwardedFor) throws Exception {
        var request = post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(user.getUsername(), password))
                .with(r -> {
                    r.setRemoteAddr(ip);
                    return r;
                });
        if (forwardedFor != null) {
            request.header("X-Forwarded-For", forwardedFor);
        }
        return mockMvc.perform(request).andReturn();
    }

    @Test
    void repeatedFailedLoginsAreBlockedEvenWithTheRightPassword() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertError(login("wrong", "10.0.0.1", null), 401, "INVALID_CREDENTIALS");
        }

        MvcResult blocked = login(PASSWORD, "10.0.0.1", null);
        assertError(blocked, 429, "TOO_MANY_ATTEMPTS");
        long retryAfter = Long.parseLong(blocked.getResponse().getHeader("Retry-After"));
        assertThat(retryAfter).isBetween(1L, 15L * 60);

        // X-Forwarded-For is not trusted: a spoofed header does not give a fresh budget
        assertError(login(PASSWORD, "10.0.0.1", "203.0.113.9"), 429, "TOO_MANY_ATTEMPTS");
        // another client IP has its own budget
        assertThat(login(PASSWORD, "10.0.0.2", null).getResponse().getStatus()).isEqualTo(200);

        clock.set(clock.instant().plusSeconds(retryAfter));                 // one failure refilled
        assertThat(login(PASSWORD, "10.0.0.1", null).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void successfulLoginResetsTheFailureCount() throws Exception {
        login("wrong", "10.0.0.3", null);
        login("wrong", "10.0.0.3", null);
        assertThat(login(PASSWORD, "10.0.0.3", null).getResponse().getStatus()).isEqualTo(200);
        login("wrong", "10.0.0.3", null);
        login("wrong", "10.0.0.3", null);

        assertThat(login(PASSWORD, "10.0.0.3", null).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void transfersPerCustomerAreLimitedWithRetryAfter() throws Exception {
        Account source = account(user, "1000.00");
        Account receiver = account(customer(), "0");
        for (int i = 0; i < 5; i++) {
            assertThat(postTransfer(user, "key-rate-00" + i, body(source, receiver, "1.00")).getResponse().getStatus())
                    .isEqualTo(201);
        }

        MvcResult limited = postTransfer(user, "key-rate-006", body(source, receiver, "1.00"));
        assertError(limited, 429, "TOO_MANY_REQUESTS");
        assertThat(Long.parseLong(limited.getResponse().getHeader("Retry-After"))).isBetween(1L, 60L);
        assertThat(balance(source)).isEqualByComparingTo("995.00");

        // another customer is not affected
        Customer other = customer();
        assertThat(postTransfer(other, "key-rate-other", body(account(other, "10"), receiver, "1.00"))
                .getResponse().getStatus()).isEqualTo(201);

        clock.set(clock.instant().plusSeconds(60));                          // full refill
        assertThat(postTransfer(user, "key-rate-007", body(source, receiver, "1.00")).getResponse().getStatus())
                .isEqualTo(201);
    }
}
