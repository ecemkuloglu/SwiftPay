package com.swiftpay;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneId;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Starts the app against a real PostgreSQL and checks that Flyway applied schema + dev seed. */
@SpringBootTest
@ActiveProfiles("dev")
@Testcontainers
class SwiftPayApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @Test
    void contextLoadsAndMigrationsApply() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void devSeedIsLoaded() {
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customer", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM account", Integer.class)).isEqualTo(4);
    }

    @Test
    void clockUsesBusinessTimeZone() {
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Europe/Istanbul"));
    }
}
