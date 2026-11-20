package com.delivery.shipment.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class IdempotencyScopeMigrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private JdbcTemplate jdbc;

    @BeforeEach
    void migrateToLegacySchemaWithData() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
        flyway(dataSource, "1").migrate();

        jdbc.update("""
                INSERT INTO shipments (shipment_id, user_id, recipient_name, recipient_phone, pickup_address,
                    delivery_address, weight, length, width, height, distance, price, status, created_at)
                VALUES (1, 42, 'Marta', '+380671234567', 'A', 'B', 1, 1, 1, 1, 10, 100, 'WAITING_FOR_COURIER', now())
                """);
        jdbc.update("INSERT INTO idempotency_records (idempotency_key, request_hash, shipment_id, created_at) VALUES ('k', 'h', 1, now())");
        jdbc.update("INSERT INTO idempotency_records (idempotency_key, request_hash, shipment_id, created_at) VALUES ('orphan', 'h', 999, now())");

        flyway(dataSource, null).migrate();
    }

    @Test
    void backfillsSenderAndDropsOrphanRecords() {
        assertThat(jdbc.queryForList("SELECT idempotency_key || ':' || user_id FROM idempotency_records", String.class))
                .containsExactly("k:42");
    }

    @Test
    void sameKeyIsUniquePerSenderOnly() {
        jdbc.update("INSERT INTO idempotency_records (user_id, idempotency_key, request_hash, shipment_id, created_at) VALUES (43, 'k', 'h', 1, now())");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO idempotency_records (user_id, idempotency_key, request_hash, shipment_id, created_at) VALUES (42, 'k', 'h', 1, now())"))
                .hasMessageContaining("uq_idempotency_sender_key");
    }

    private static Flyway flyway(DriverManagerDataSource dataSource, String target) {
        var config = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration");
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }
}
