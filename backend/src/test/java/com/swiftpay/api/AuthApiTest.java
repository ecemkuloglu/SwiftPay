package com.swiftpay.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.swiftpay.domain.Customer;
import com.swiftpay.support.IntegrationTestBase;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

class AuthApiTest extends IntegrationTestBase {

    private static final String PASSWORD = "Sup3r-Secret!";

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Customer customer;

    @BeforeEach
    void createUser() {
        customer = customerRepository.save(new Customer(TEST_PREFIX + "login-" + System.nanoTime(),
                passwordEncoder.encode(PASSWORD), "Login Test", Instant.EPOCH));
    }

    private MvcResult login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))))
                .andReturn();
    }

    // ---- login ---------------------------------------------------------------------------------

    @Test
    void loginReturnsAWorkingBearerToken() throws Exception {
        MvcResult result = login(customer.getUsername(), PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json(result);
        assertThat(body.at("/data/tokenType").asText()).isEqualTo("Bearer");
        assertThat(body.at("/data/expiresIn").asLong()).isEqualTo(900);
        assertThat(body.at("/traceId").asText()).isEqualTo(result.getResponse().getHeader("X-Trace-Id"));
        assertThat(body.at("/timestamp").asText()).isEqualTo("2026-05-14T09:30:00Z");

        String token = body.at("/data/accessToken").asText();
        mockMvc.perform(get("/api/test/whoami").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerId").value(customer.getId()));
    }

    @Test
    void unknownUserAndWrongPasswordGiveTheSameError() throws Exception {
        MvcResult wrongPassword = login(customer.getUsername(), "wrong-password");
        MvcResult unknownUser = login("no-such-user", PASSWORD);

        for (MvcResult result : List.of(wrongPassword, unknownUser)) {
            assertThat(result.getResponse().getStatus()).isEqualTo(401);
            assertThat(json(result).get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
        }
        assertThat(json(wrongPassword).get("message")).isEqualTo(json(unknownUser).get("message"));
        assertThat(json(wrongPassword).has("fieldErrors")).isFalse();
    }

    @Test
    void failedLoginsAreAuditedWithoutThePassword() throws Exception {
        login(customer.getUsername(), "wrong-password-123");
        login("unknown-user-xyz", "another-secret-456");

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT customer_id, details FROM audit_log WHERE event_type = 'LOGIN_FAILED' "
                        + "AND (customer_id = ? OR customer_id IS NULL)", customer.getId());
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(r -> r.get("customer_id")).containsExactlyInAnyOrder(customer.getId(), null);
        assertThat(rows).extracting(r -> (String) r.get("details"))
                .noneMatch(d -> d.contains("wrong-password-123") || d.contains("another-secret-456")
                        || d.contains("unknown-user-xyz"));
    }

    @Test
    void loginRequestIsValidated() throws Exception {
        MvcResult blank = login("", "");
        assertThat(blank.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(blank).get("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(json(blank).get("fieldErrors").findValuesAsText("field"))
                .containsExactlyInAnyOrder("username", "password");

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"a\",\"password\":\"b\",\"admin\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("admin"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Malformed request body."));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":[1],\"password\":\"b\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("username"));
    }

    // ---- token validation: every bad token gets the same 401 ------------------------------------

    @Test
    void validTokenIsAccepted() throws Exception {
        mockMvc.perform(get("/api/test/whoami").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isOk());
    }

    @Test
    void missingToken() throws Exception {
        assertUnauthorized(null);
    }

    @Test
    void malformedToken() throws Exception {
        assertUnauthorized("Bearer not-a-jwt");
        assertUnauthorized("Bearer ");
        assertUnauthorized("Basic " + Base64.getEncoder().encodeToString("ecem:Test1234!".getBytes()));
    }

    @Test
    void expiredToken() throws Exception {
        String token = bearer(customer);                        // issued at 09:30:00, valid 900 s

        clock.set(clock.instant().plusSeconds(899));
        mockMvc.perform(get("/api/test/whoami").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk());

        clock.set(clock.instant().plusSeconds(2));
        assertUnauthorized(token);
    }

    @Test
    void tokenSignedWithAnotherKey() throws Exception {
        String forged = Jwts.builder().subject(customer.getId().toString())
                .expiration(Date.from(clock.instant().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor("another-secret-another-secret-another-secret".getBytes(
                        StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
        assertUnauthorized("Bearer " + forged);
    }

    @Test
    void tamperedPayload() throws Exception {
        String[] parts = jwtService.issue(customer.getId()).split("\\.");
        String otherSubject = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"1\",\"exp\":9999999999}".getBytes(StandardCharsets.UTF_8));
        assertUnauthorized("Bearer " + parts[0] + "." + otherSubject + "." + parts[2]);
    }

    @Test
    void unsignedAlgNoneToken() throws Exception {
        String unsigned = Jwts.builder().subject(customer.getId().toString())
                .expiration(Date.from(clock.instant().plusSeconds(600))).compact();
        assertUnauthorized("Bearer " + unsigned);
    }

    @Test
    void validTokenOfADeletedCustomer() throws Exception {
        String token = bearer(customer);
        customerRepository.deleteById(customer.getId());
        assertUnauthorized(token);
    }

    private void assertUnauthorized(String authorization) throws Exception {
        var request = get("/api/test/whoami");
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        MvcResult result = mockMvc.perform(request).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        JsonNode body = json(result);
        assertThat(body.get("code").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(body.get("message").asText()).isEqualTo("Authentication is required.");
        assertThat(body.get("traceId").asText()).isEqualTo(result.getResponse().getHeader("X-Trace-Id"));
    }
}
