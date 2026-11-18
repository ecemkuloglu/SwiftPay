package com.swiftpay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swiftpay.support.PostgresContainerSupport;
import java.time.Clock;
import java.time.ZoneId;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Starts the full app (dev profile) against real PostgreSQL. Context startup itself proves that Flyway ran and
 * that Hibernate's ddl-auto=validate accepted every entity mapping.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SwiftPayApplicationTests extends PostgresContainerSupport {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoadsAndMigrationsApply() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void devSeedIsLoaded() {
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customer WHERE username IN ('ecem', 'ali')",
                Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM account WHERE id BETWEEN 1 AND 4",
                Integer.class)).isEqualTo(4);
    }

    @Test
    void clockUsesBusinessTimeZone() {
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Europe/Istanbul"));
    }

    @Test
    void seededUserCanLogIn() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ecem\",\"password\":\"Test1234!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").isString());
    }
}
