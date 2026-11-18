package com.swiftpay.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.swiftpay.domain.Account;
import com.swiftpay.domain.Customer;
import com.swiftpay.support.ApiTestBase;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

class AccountAndStatisticsApiTest extends ApiTestBase {

    private JsonNode data(Customer customer, String url) throws Exception {
        MvcResult result = mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, bearer(customer))).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(result).get("traceId").asText()).isEqualTo(result.getResponse().getHeader("X-Trace-Id"));
        return json(result).get("data");
    }

    @Test
    void accountsAreTheCallersOnlyWithStringBalances() throws Exception {
        Customer ecem = customer();
        Account first = account(ecem, "250000");
        Account second = account(ecem, "10000.5");
        account(customer(), "1.00");

        JsonNode accounts = data(ecem, "/api/accounts");

        assertThat(accounts).hasSize(2);
        JsonNode a = accounts.get(0);
        assertThat(fieldNames(a)).isEqualTo(Set.of("id", "iban", "maskedIban", "currency", "balance"));
        assertThat(a.get("id").asLong()).isEqualTo(first.getId());
        assertThat(a.get("iban").asText()).isEqualTo(first.getIban());
        assertThat(a.get("maskedIban").asText()).isEqualTo(
                first.getIban().substring(0, 4) + " **** **** **** **** " + first.getIban().substring(22));
        assertThat(a.get("currency").asText()).isEqualTo("TRY");
        assertThat(a.get("balance").isTextual()).isTrue();
        assertThat(a.get("balance").asText()).isEqualTo("250000.00");
        assertThat(accounts.get(1).get("id").asLong()).isEqualTo(second.getId());
        assertThat(accounts.get(1).get("balance").asText()).isEqualTo("10000.50");
    }

    @Test
    void limitsHaveTheSpecShapeAndCountOnlyCompleted() throws Exception {
        Customer ecem = customer();
        Account source = account(ecem, "100000.00");
        Account receiver = account(customer(), "0");
        postTransfer(ecem, "key-limits-001", body(source, receiver, "35000.00"));
        postTransfer(ecem, "key-limits-002", body(source, receiver, "60000.00"));      // FAILED (per-transfer)

        JsonNode limits = data(ecem, "/api/accounts/limits");

        assertThat(fieldNames(limits)).isEqualTo(Set.of("currency", "perTransferLimit", "daily", "monthly"));
        assertThat(limits.get("perTransferLimit").asText()).isEqualTo("50000.00");
        assertThat(fieldNames(limits.get("daily"))).isEqualTo(Set.of("limit", "used", "remaining"));
        assertThat(limits.at("/daily/limit").asText()).isEqualTo("100000.00");
        assertThat(limits.at("/daily/used").asText()).isEqualTo("35000.00");
        assertThat(limits.at("/daily/remaining").asText()).isEqualTo("65000.00");
        assertThat(limits.at("/monthly/limit").asText()).isEqualTo("500000.00");
        assertThat(limits.at("/monthly/remaining").asText()).isEqualTo("465000.00");
    }

    @Test
    void monthlyStatisticsSixRowsNewestFirstZeroFilled() throws Exception {
        Customer ecem = customer();
        Account source = account(ecem, "1000.00");
        Account receiver = account(customer(), "0");
        clock.set(Instant.parse("2026-04-30T21:30:00Z"));                                // May 1 in Istanbul
        postTransfer(ecem, "key-stats-0001", body(source, receiver, "100.00"));
        clock.set(Instant.parse("2026-05-14T09:30:00Z"));

        JsonNode stats = data(ecem, "/api/statistics/monthly");

        assertThat(stats).hasSize(6);
        assertThat(fieldNames(stats.get(0))).isEqualTo(Set.of("month", "totalTransferCount", "totalTransferredAmount"));
        assertThat(stats.findValuesAsText("month"))
                .containsExactly("2026-05", "2026-04", "2026-03", "2026-02", "2026-01", "2025-12");
        assertThat(stats.get(0).get("totalTransferCount").asLong()).isEqualTo(1);
        assertThat(stats.get(0).get("totalTransferredAmount").asText()).isEqualTo("100.00");
        assertThat(stats.get(1).get("totalTransferredAmount").isTextual()).isTrue();
        assertThat(stats.get(1).get("totalTransferredAmount").asText()).isEqualTo("0.00");
    }
}
