package com.swiftpay.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.swiftpay.domain.Account;
import com.swiftpay.domain.Customer;
import com.swiftpay.support.ApiTestBase;
import com.swiftpay.support.TestIbans;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

class TransferApiTest extends ApiTestBase {

    private Customer ecem;
    private Account ecemMain;
    private Account ecemSmall;
    private Customer ali;
    private Account aliMain;

    @BeforeEach
    void fixtures() {
        ecem = customer();
        ecemMain = account(ecem, "250000.00");
        ecemSmall = account(ecem, "10000.00");
        ali = customer();
        aliMain = account(ali, "100000.00");
    }

    private MvcResult get(Customer customer, String url) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url)
                .header(HttpHeaders.AUTHORIZATION, bearer(customer))).andReturn();
    }

    // ---- create + replay ----------------------------------------------------------------------

    @Test
    void createReturns201WithExactlyTheSpecFields() throws Exception {
        MvcResult result = postTransfer(ecem, "key-create-001", body(ecemMain, aliMain, "1500"));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(result.getResponse().getHeader("Idempotent-Replayed")).isNull();
        JsonNode data = json(result).get("data");
        assertThat(fieldNames(data)).isEqualTo(Set.of("id", "sourceAccountId", "receiverIban", "maskedReceiverIban",
                "amount", "currency", "description", "status", "createdAt", "completedAt"));
        assertThat(data.get("amount").isTextual()).isTrue();
        assertThat(data.get("amount").asText()).isEqualTo("1500.00");
        assertThat(data.get("currency").asText()).isEqualTo("TRY");
        assertThat(data.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(data.get("receiverIban").asText()).isEqualTo(aliMain.getIban());
        assertThat(data.get("maskedReceiverIban").asText()).matches("TR\\d{2} \\*{4} \\*{4} \\*{4} \\*{4} \\d{4}");
        assertThat(data.get("createdAt").asText()).isEqualTo("2026-05-14T09:30:00Z");
        assertThat(data.get("completedAt").asText()).isEqualTo("2026-05-14T09:30:00Z");
        assertThat(json(result).get("traceId").asText()).isEqualTo(result.getResponse().getHeader("X-Trace-Id"));
    }

    @Test
    void amountAsJsonNumberIsAcceptedToo() throws Exception {
        MvcResult result = postTransfer(ecem, "key-number-01",
                "{\"sourceAccountId\": %d, \"receiverIban\": \"%s\", \"amount\": 12.5}"
                        .formatted(ecemMain.getId(), aliMain.getIban()));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(json(result).at("/data/amount").asText()).isEqualTo("12.50");
    }

    @Test
    void replayReturns200WithTheOriginalBodyAndHeader() throws Exception {
        MvcResult first = postTransfer(ecem, "key-replay-01", body(ecemMain, aliMain, "1500.00"));
        MvcResult replay = postTransfer(ecem, "key-replay-01", body(ecemMain, aliMain, "1500"));

        assertThat(replay.getResponse().getStatus()).isEqualTo(200);
        assertThat(replay.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("true");
        assertThat(json(replay).get("data")).isEqualTo(json(first).get("data"));
        assertThat(balance(ecemMain)).isEqualByComparingTo("248500.00");
    }

    @Test
    void sameKeyDifferentPayloadIs409() throws Exception {
        postTransfer(ecem, "key-conflict-1", body(ecemMain, aliMain, "1500.00"));
        assertError(postTransfer(ecem, "key-conflict-1", body(ecemMain, aliMain, "2500.00")), 409,
                "IDEMPOTENCY_KEY_CONFLICT");
    }

    @Test
    void idempotencyKeyIsRequiredAndValidated() throws Exception {
        JsonNode missing = assertError(postTransfer(ecem, null, body(ecemMain, aliMain, "1.00")), 400,
                "IDEMPOTENCY_KEY_REQUIRED");
        assertThat(missing.has("fieldErrors")).isFalse();
        assertError(postTransfer(ecem, "   ", body(ecemMain, aliMain, "1.00")), 400, "IDEMPOTENCY_KEY_REQUIRED");

        for (String bad : new String[]{"short", "has space in it", "semi;colon-key", "k".repeat(65)}) {
            refillRateLimit();
            JsonNode error = assertError(postTransfer(ecem, bad, body(ecemMain, aliMain, "1.00")), 400,
                    "VALIDATION_ERROR");
            assertThat(error.at("/fieldErrors/0/field").asText()).isEqualTo("Idempotency-Key");
        }
        // missing key is reported before an invalid body
        assertError(postTransfer(ecem, null, "{\"amount\": \"-1\"}"), 400, "IDEMPOTENCY_KEY_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transfer WHERE customer_id = ?", Long.class,
                ecem.getId())).isZero();
    }

    // ---- body validation ----------------------------------------------------------------------

    @Test
    void bodyValidationErrorsHaveFieldErrors() throws Exception {
        // each assertField refills the rate-limit bucket first
        assertField(body(ecemMain, aliMain, "-5.00"), "amount");
        assertField(body(ecemMain, aliMain, "0"), "amount");
        assertField(body(ecemMain, aliMain, "10.001"), "amount");
        assertField(body(ecemMain, aliMain, "123456789012345678.00"), "amount");   // 18 integer digits
        assertField(body(ecemMain.getId(), "TR12345", "10.00"), "receiverIban");
        assertField(body(ecemMain.getId(), "TR640006200000000000000002", "10.00"), "receiverIban"); // bad checksum
        assertField("{\"receiverIban\": \"%s\", \"amount\": \"1\"}".formatted(aliMain.getIban()), "sourceAccountId");
        assertField("{\"sourceAccountId\": %d, \"receiverIban\": \"%s\", \"amount\": \"1\", \"description\": \"%s\"}"
                .formatted(ecemMain.getId(), aliMain.getIban(), "x".repeat(141)), "description");
        assertField("{\"sourceAccountId\": %d, \"receiverIban\": \"%s\", \"amount\": \"abc\"}"
                .formatted(ecemMain.getId(), aliMain.getIban()), "amount");
        assertField("{\"sourceAccountId\": %d, \"receiverIban\": \"%s\", \"amount\": \"1\", \"customerId\": 99}"
                .formatted(ecemMain.getId(), aliMain.getIban()), "customerId");
        refillRateLimit();
        assertError(postTransfer(ecem, "key-malformed", "{oops"), 400, "VALIDATION_ERROR");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transfer WHERE customer_id = ?", Long.class,
                ecem.getId())).isZero();
    }

    @Test
    void ibanWithSpacesAndLowerCaseIsAccepted() throws Exception {
        String spaced = aliMain.getIban().toLowerCase().replaceAll("(.{4})", "$1 ");
        assertThat(postTransfer(ecem, "key-spaced-1", body(ecemMain.getId(), spaced, "1.00")).getResponse()
                .getStatus()).isEqualTo(201);
    }

    private int keySeq;

    private void assertField(String body, String field) throws Exception {
        refillRateLimit();
        JsonNode error = assertError(postTransfer(ecem, "key-validation-" + keySeq++, body), 400, "VALIDATION_ERROR");
        assertThat(error.get("fieldErrors").findValuesAsText("field")).contains(field);
    }

    // ---- business errors over HTTP ------------------------------------------------------------

    @Test
    void insufficientBalanceIs422() throws Exception {
        assertError(postTransfer(ecem, "key-insuff-01", body(ecemSmall, aliMain, "20000.00")), 422,
                "INSUFFICIENT_BALANCE");
    }

    @Test
    void perTransferLimitIs422() throws Exception {
        assertError(postTransfer(ecem, "key-pertx-001", body(ecemMain, aliMain, "50000.01")), 422,
                "PER_TRANSFER_LIMIT_EXCEEDED");
    }

    @Test
    void dailyLimitIs422() throws Exception {
        postTransfer(ecem, "key-daily-001", body(ecemMain, aliMain, "50000.00"));
        postTransfer(ecem, "key-daily-002", body(ecemMain, aliMain, "50000.00"));
        assertError(postTransfer(ecem, "key-daily-003", body(ecemMain, aliMain, "0.01")), 422,
                "DAILY_LIMIT_EXCEEDED");
    }

    @Test
    void monthlyLimitIs422() throws Exception {
        Account rich = account(ecem, "1000000.00");
        for (int day = 0; day < 5; day++) {
            clock.set(Instant.parse("2026-05-20T09:00:00Z").plusSeconds(86_400L * day));
            postTransfer(ecem, "key-month-a" + day, body(rich, aliMain, "50000.00"));
            postTransfer(ecem, "key-month-b" + day, body(rich, aliMain, "50000.00"));
        }
        clock.set(Instant.parse("2026-05-26T09:00:00Z"));
        assertError(postTransfer(ecem, "key-month-over", body(rich, aliMain, "0.01")), 422, "MONTHLY_LIMIT_EXCEEDED");
    }

    @Test
    void sameAccountIs422() throws Exception {
        assertError(postTransfer(ecem, "key-same-0001", body(ecemMain, ecemMain, "10.00")), 422,
                "SAME_ACCOUNT_TRANSFER");
    }

    @Test
    void unknownReceiverIs404() throws Exception {
        assertError(postTransfer(ecem, "key-unknown-01", body(ecemMain.getId(), TestIbans.next(), "10.00")), 404,
                "RECEIVER_NOT_FOUND");
    }

    @Test
    void foreignSourceAccountIs403() throws Exception {
        assertError(postTransfer(ecem, "key-foreign-01", body(aliMain, ecemMain, "10.00")), 403, "ACCOUNT_NOT_OWNED");
        assertThat(balance(aliMain)).isEqualByComparingTo("100000.00");
    }

    // ---- read: detail ------------------------------------------------------------------------

    @Test
    void detailHasSourceMaskedIbanAndIsScopedToTheOwner() throws Exception {
        long id = json(postTransfer(ecem, "key-detail-01", body(ecemMain, aliMain, "1.00"))).at("/data/id").asLong();

        MvcResult own = get(ecem, "/api/transfers/" + id);
        assertThat(own.getResponse().getStatus()).isEqualTo(200);
        JsonNode data = json(own).get("data");
        assertThat(fieldNames(data)).contains("sourceAccountMaskedIban").doesNotContain("failureReason");
        assertThat(data.get("sourceAccountMaskedIban").asText()).endsWith(ecemMain.getIban().substring(22))
                .contains("****");

        assertError(get(ali, "/api/transfers/" + id), 404, "TRANSFER_NOT_FOUND");      // IDOR
        assertError(get(ecem, "/api/transfers/999999999"), 404, "TRANSFER_NOT_FOUND");
        assertError(get(ecem, "/api/transfers/abc"), 400, "VALIDATION_ERROR");
    }

    @Test
    void failedTransferDetailHasAHumanReadableReason() throws Exception {
        postTransfer(ecem, "key-failed-01", body(ecemSmall, aliMain, "20000.00"));
        Long id = jdbc.queryForObject("SELECT id FROM transfer WHERE customer_id = ?", Long.class, ecem.getId());

        JsonNode data = json(get(ecem, "/api/transfers/" + id)).get("data");
        assertThat(data.get("status").asText()).isEqualTo("FAILED");
        assertThat(data.get("failureReason").asText()).isEqualTo("Insufficient balance.");
        assertThat(data.get("completedAt").isNull()).isTrue();
    }

    // ---- read: list --------------------------------------------------------------------------

    @Test
    void listHasThePageShapeMaskedIbansAndSortOrder() throws Exception {
        for (int i = 0; i < 3; i++) {
            clock.set(Instant.parse("2026-05-14T09:30:00Z").plusSeconds(i));
            postTransfer(ecem, "key-list-00" + i, body(ecemMain, aliMain, (i + 1) + ".00"));
        }

        JsonNode page = json(get(ecem, "/api/transfers?page=0&size=2&sort=createdAt,desc")).get("data");
        assertThat(fieldNames(page)).isEqualTo(Set.of("content", "page", "size", "totalElements", "totalPages"));
        assertThat(page.get("totalElements").asLong()).isEqualTo(3);
        assertThat(page.get("totalPages").asInt()).isEqualTo(2);
        JsonNode item = page.get("content").get(0);
        assertThat(fieldNames(item)).isEqualTo(Set.of("id", "maskedReceiverIban", "amount", "status", "createdAt"));
        assertThat(item.get("amount").asText()).isEqualTo("3.00");
        assertThat(item.get("maskedReceiverIban").asText()).contains("****").doesNotContain(aliMain.getIban());

        JsonNode asc = json(get(ecem, "/api/transfers?sort=createdAt,asc")).at("/data/content");
        assertThat(asc.get(0).get("amount").asText()).isEqualTo("1.00");
        assertThat(json(get(ecem, "/api/transfers")).at("/data/size").asInt()).isEqualTo(10);   // default
        assertThat(json(get(ali, "/api/transfers")).at("/data/totalElements").asLong()).isZero();
        assertThat(json(get(ecem, "/api/transfers?customerId=" + ali.getId())).at("/data/totalElements").asLong())
                .isEqualTo(3);                                                                // client id ignored
    }

    @Test
    void listParametersAreValidated() throws Exception {
        assertError(get(ecem, "/api/transfers?sort=amount,desc"), 400, "VALIDATION_ERROR");
        assertError(get(ecem, "/api/transfers?sort=createdAt"), 400, "VALIDATION_ERROR");
        assertError(get(ecem, "/api/transfers?size=101"), 400, "VALIDATION_ERROR");
        assertError(get(ecem, "/api/transfers?size=0"), 400, "VALIDATION_ERROR");
        assertError(get(ecem, "/api/transfers?page=-1"), 400, "VALIDATION_ERROR");
        assertError(get(ecem, "/api/transfers?page=abc"), 400, "VALIDATION_ERROR");
        assertError(get(ecem, "/api/transfers?startDate=14-05-2026"), 400, "VALIDATION_ERROR");
        assertError(get(ecem, "/api/transfers?startDate=2026-05-31&endDate=2026-05-01"), 400, "INVALID_DATE_RANGE");
        assertThat(get(ecem, "/api/transfers?startDate=2026-05-01&endDate=2026-05-01").getResponse().getStatus())
                .isEqualTo(200);
    }

    @Test
    void everyTransferEndpointNeedsAToken() throws Exception {
        for (String url : new String[]{"/api/transfers", "/api/transfers/1", "/api/accounts", "/api/accounts/limits",
                "/api/statistics/monthly"}) {
            assertError(mockMvc.perform(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url)).andReturn(),
                    401, "UNAUTHORIZED");
        }
    }
}
