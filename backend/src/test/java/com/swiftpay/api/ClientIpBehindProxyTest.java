package com.swiftpay.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftpay.domain.Customer;
import com.swiftpay.repository.CustomerRepository;
import com.swiftpay.support.PostgresContainerSupport;
import com.swiftpay.support.TestClockConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Client IP through the REAL embedded Tomcat (MockMvc bypasses the RemoteIpValve), observed via the
 * login failure limit (test config: 3 failures per username + IP). Requests come from 127.0.0.1.
 */
class ClientIpBehindProxyTest {

    abstract static class RealServerBase extends PostgresContainerSupport {

        @LocalServerPort
        int port;
        @Autowired
        CustomerRepository customerRepository;
        @Autowired
        PasswordEncoder passwordEncoder;
        @Autowired
        JdbcTemplate jdbc;

        private final HttpClient http = HttpClient.newHttpClient();

        String username;

        String createUser() {
            username = "it-ip-" + System.nanoTime();
            customerRepository.save(new Customer(username, passwordEncoder.encode("Right-Pass-1"), "IP Test",
                    Instant.EPOCH));
            return username;
        }

        int login(String password, String forwardedFor) throws Exception {
            HttpRequest.Builder request = HttpRequest.newBuilder(
                            URI.create("http://127.0.0.1:" + port + "/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)));
            if (forwardedFor != null) {
                request.header("X-Forwarded-For", forwardedFor);
            }
            return http.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
        }

        void failThreeTimes(String forwardedFor) throws Exception {
            for (int i = 0; i < 3; i++) {
                assertThat(login("wrong", forwardedFor)).isEqualTo(401);
            }
        }

        @AfterEach
        void cleanUp() {
            jdbc.update("DELETE FROM audit_log WHERE customer_id IN (SELECT id FROM customer WHERE username = ?)"
                    + " OR customer_id IS NULL", username);
            jdbc.update("DELETE FROM customer WHERE username = ?", username);
        }
    }

    /** Default configuration: no trusted proxy, so a client-sent X-Forwarded-For must be ignored. */
    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @Import(TestClockConfig.class)
    class NoTrustedProxy extends RealServerBase {

        @Test
        void spoofedForwardedForDoesNotGiveAFreshBudget() throws Exception {
            createUser();
            failThreeTimes("198.51.100.1");

            assertThat(login("Right-Pass-1", "198.51.100.2")).isEqualTo(429);
            assertThat(login("Right-Pass-1", null)).isEqualTo(429);
        }
    }

    /** 127.0.0.1 plays the nginx container: its X-Forwarded-For is the real client IP. */
    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = "server.tomcat.remoteip.internal-proxies=127\\.0\\.0\\.1")
    @Import(TestClockConfig.class)
    class LoopbackIsTheTrustedProxy extends RealServerBase {

        @Test
        void eachForwardedClientHasItsOwnBudget() throws Exception {
            createUser();
            failThreeTimes("198.51.100.1");

            assertThat(login("Right-Pass-1", "198.51.100.1")).isEqualTo(429);   // same client: blocked
            assertThat(login("Right-Pass-1", "198.51.100.2")).isEqualTo(200);   // another client: not affected
        }
    }
}
