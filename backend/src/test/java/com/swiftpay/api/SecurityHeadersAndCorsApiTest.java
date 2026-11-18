package com.swiftpay.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

import com.swiftpay.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

class SecurityHeadersAndCorsApiTest extends IntegrationTestBase {

    @Test
    void securityHeadersOnSuccessAndOnErrors() throws Exception {
        for (MockHttpServletResponse response : new MockHttpServletResponse[]{
                mockMvc.perform(get("/api/test/whoami").header(HttpHeaders.AUTHORIZATION, bearer(customer())))
                        .andReturn().getResponse(),
                mockMvc.perform(get("/api/test/whoami")).andReturn().getResponse()}) {
            assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
            assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
            assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        }
    }

    @Test
    void preflightFromAnAllowedOrigin() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(options("/api/transfers")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type, Idempotency-Key"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo("http://localhost:5173");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS))
                .containsIgnoringCase("idempotency-key").containsIgnoringCase("authorization");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isNull();
    }

    @Test
    void preflightFromADisallowedOriginIsRejected() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(options("/api/transfers")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
    }

    @Test
    void preflightWithANonWhitelistedHeaderIsRejected() throws Exception {
        assertThat(mockMvc.perform(options("/api/transfers")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Custom"))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void actualCorsResponseExposesTraceAndReplayHeaders() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/api/test/whoami")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customer())))
                .andReturn().getResponse();

        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo("http://localhost:3000");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS))
                .contains("X-Trace-Id", "Idempotent-Replayed", "Retry-After");
    }
}
