package com.swiftpay.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.swiftpay.domain.Account;
import com.swiftpay.domain.Customer;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** Helpers for HTTP-level tests. */
public abstract class ApiTestBase extends IntegrationTestBase {

    protected MvcResult postTransfer(Customer customer, String idempotencyKey, String jsonBody) throws Exception {
        var request = post("/api/transfers").header(HttpHeaders.AUTHORIZATION, bearer(customer))
                .contentType(MediaType.APPLICATION_JSON).content(jsonBody);
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(request).andReturn();
    }

    /**
     * Moves the test clock one rate-limit window ahead so the per-customer transfer bucket is full again
     * (rate limiting runs before validation, so invalid requests use up tokens too).
     */
    protected void refillRateLimit() {
        clock.set(clock.instant().plusSeconds(60));
    }

    protected static String body(Long sourceAccountId, String receiverIban, String amount) {
        return """
                {"sourceAccountId": %d, "receiverIban": "%s", "amount": "%s", "description": "Kira"}
                """.formatted(sourceAccountId, receiverIban, amount);
    }

    protected static String body(Account source, Account receiver, String amount) {
        return body(source.getId(), receiver.getIban(), amount);
    }

    /** Error envelope: status, code, a message, and the same trace id in body and header. */
    protected JsonNode assertError(MvcResult result, int status, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        JsonNode body = json(result);
        assertThat(body.get("code").asText()).isEqualTo(code);
        assertThat(body.get("message").asText()).isNotBlank();
        assertThat(body.get("traceId").asText()).isEqualTo(result.getResponse().getHeader("X-Trace-Id"));
        assertThat(body.get("timestamp").isTextual()).isTrue();
        return body;
    }

    protected static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
            names.add(it.next());
        }
        return names;
    }
}
