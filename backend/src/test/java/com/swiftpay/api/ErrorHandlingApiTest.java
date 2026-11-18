package com.swiftpay.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.swiftpay.domain.Customer;
import com.swiftpay.support.IntegrationTestBase;
import jakarta.servlet.RequestDispatcher;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

@ExtendWith(OutputCaptureExtension.class)
class ErrorHandlingApiTest extends IntegrationTestBase {

    private String token;

    @BeforeEach
    void user() {
        Customer customer = customer();
        token = bearer(customer);
    }

    private MvcResult call(RequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn();
    }

    /** Every response: X-Trace-Id header present, 16 hex chars, equal to the body's traceId. */
    private JsonNode assertTraced(MvcResult result, int expectedStatus) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus);
        String header = result.getResponse().getHeader("X-Trace-Id");
        assertThat(header).matches("[0-9a-f]{16}");
        JsonNode body = json(result);
        assertThat(body.get("traceId").asText()).isEqualTo(header);
        assertThat(body.get("timestamp").asText()).isEqualTo("2026-05-14T09:30:00Z");
        return body;
    }

    @Test
    void traceIdOnSuccessAndEveryErrorStatus() throws Exception {
        assertTraced(call(get("/api/test/whoami").header(HttpHeaders.AUTHORIZATION, token)), 200);
        assertTraced(call(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}")), 400);
        assertTraced(call(get("/api/test/whoami")), 401);
        assertTraced(call(get("/api/does-not-exist").header(HttpHeaders.AUTHORIZATION, token)), 404);
        assertTraced(call(put("/api/test/whoami").header(HttpHeaders.AUTHORIZATION, token)), 405);
        assertTraced(call(get("/api/test/boom").header(HttpHeaders.AUTHORIZATION, token)), 500);
    }

    @Test
    void clientSuppliedTraceIdIsIgnoredAndEveryRequestGetsANewOne() throws Exception {
        MvcResult first = call(get("/api/test/whoami").header("X-Trace-Id", "attacker-chosen"));
        MvcResult second = call(get("/api/test/whoami"));

        assertThat(first.getResponse().getHeader("X-Trace-Id")).isNotEqualTo("attacker-chosen")
                .isNotEqualTo(second.getResponse().getHeader("X-Trace-Id"));
    }

    @Test
    void unexpectedErrorIsGeneric500WithoutInternalsButLoggedWithTraceId(CapturedOutput output) throws Exception {
        MvcResult result = call(get("/api/test/boom").header(HttpHeaders.AUTHORIZATION, token));

        JsonNode body = assertTraced(result, 500);
        String raw = result.getResponse().getContentAsString();
        assertThat(body.get("code").asText()).isEqualTo("INTERNAL_ERROR");
        assertThat(body.get("message").asText()).isEqualTo("An unexpected error occurred.");
        assertThat(raw).doesNotContain("IllegalStateException", "hunter2", "internal.Secret", "Exception", "at com.");
        assertThat(output).contains("[" + body.get("traceId").asText() + "]")
                .contains("Unexpected error").contains("IllegalStateException");
    }

    @Test
    void unknownRouteAndWrongMethod() throws Exception {
        JsonNode notFound = assertTraced(call(get("/api/nope").header(HttpHeaders.AUTHORIZATION, token)), 404);
        assertThat(notFound.get("code").asText()).isEqualTo("NOT_FOUND");

        MvcResult wrongMethod = call(put("/api/test/whoami").header(HttpHeaders.AUTHORIZATION, token));
        assertThat(assertTraced(wrongMethod, 405).get("code").asText()).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(wrongMethod.getResponse().getHeader(HttpHeaders.ALLOW)).contains("GET");
    }

    @Test
    void unknownRouteWithoutTokenIsUnauthorizedFirst() throws Exception {
        assertThat(assertTraced(call(get("/api/nope")), 401).get("code").asText()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void badQueryParametersAreValidationErrors() throws Exception {
        JsonNode wrongType = assertTraced(call(get("/api/test/params?page=abc").header(HttpHeaders.AUTHORIZATION,
                token)), 400);
        assertThat(wrongType.get("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(wrongType.at("/fieldErrors/0/field").asText()).isEqualTo("page");

        JsonNode missing = assertTraced(call(get("/api/test/params").header(HttpHeaders.AUTHORIZATION, token)), 400);
        assertThat(missing.at("/fieldErrors/0/field").asText()).isEqualTo("page");

        JsonNode tooSmall = assertTraced(call(get("/api/test/params?page=0&size=0").header(HttpHeaders.AUTHORIZATION,
                token)), 400);
        assertThat(tooSmall.at("/fieldErrors/0/field").asText()).isEqualTo("size");
    }

    @Test
    void missingIdempotencyKeyHeaderHasItsOwnCode() throws Exception {
        JsonNode missingKey = assertTraced(call(get("/api/test/header").header(HttpHeaders.AUTHORIZATION, token)
                .header("X-Other", "x")), 400);
        assertThat(missingKey.get("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
        assertThat(missingKey.has("fieldErrors")).isFalse();

        JsonNode missingOther = assertTraced(call(get("/api/test/header").header(HttpHeaders.AUTHORIZATION, token)
                .header("Idempotency-Key", "abcdefgh")), 400);
        assertThat(missingOther.get("code").asText()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void errorControllerMapsContainerErrorsToTheStandardShape() throws Exception {
        JsonNode notFound = json(call(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404)));
        assertThat(notFound.get("code").asText()).isEqualTo("NOT_FOUND");

        MvcResult crash = call(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                .requestAttr(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("secret")));
        assertThat(crash.getResponse().getStatus()).isEqualTo(500);
        assertThat(crash.getResponse().getContentAsString()).doesNotContain("secret", "IllegalStateException");

        assertThat(json(call(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 400)))
                .get("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(json(call(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 401)))
                .get("code").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(json(call(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 405)))
                .get("code").asText()).isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    void healthIsPublicButOtherActuatorEndpointsAreNotExposed() throws Exception {
        MvcResult health = call(get("/actuator/health"));
        assertThat(health.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(health).get("status").asText()).isEqualTo("UP");

        assertThat(call(get("/actuator/env")).getResponse().getStatus()).isEqualTo(401);
        assertThat(call(get("/actuator/env").header(HttpHeaders.AUTHORIZATION, token)).getResponse().getStatus())
                .isEqualTo(404);
    }

    @Test
    void openApiDocsArePublic() throws Exception {
        MvcResult docs = call(get("/v3/api-docs"));
        assertThat(docs.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(docs).at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer");
    }

    @Test
    void moneyIsAPlainStringAndInstantsAreIsoUtc() throws Exception {
        String json = objectMapper.writeValueAsString(Map.of(
                "zero", new BigDecimal("0.00"),
                "big", new BigDecimal("2.5E+4"),
                "amount", new BigDecimal("25000.00"),
                "at", Instant.parse("2026-05-14T09:30:00Z")));

        assertThat(json).contains("\"zero\":\"0.00\"", "\"big\":\"25000\"", "\"amount\":\"25000.00\"",
                "\"at\":\"2026-05-14T09:30:00Z\"");
        assertThat(json(call(get("/api/test/whoami").header(HttpHeaders.AUTHORIZATION, token)))
                .at("/data/zero").isTextual()).isTrue();
    }
}
