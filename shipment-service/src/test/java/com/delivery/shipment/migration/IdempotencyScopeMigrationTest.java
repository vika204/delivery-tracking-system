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
        jdbc.update("UPDATE shipments SET delivery_type = 'EXPRESS' WHERE shipment_id = 1");
        jdbc.update("""
                INSERT INTO price_quotes (quote_id, user_id, pickup_address, pickup_city_code,
                    pickup_latitude, pickup_longitude, delivery_address, delivery_city_code,
                    delivery_latitude, delivery_longitude, distance_km, weight, delivery_type,
                    base_price, price_per_km, price_per_kg, distance_charge, weight_charge,
                    delivery_type_multiplier, total_price, calculated_at, expires_at)
                VALUES ('e9ea2d75-068d-4295-a860-c13ebbed0c31', 42, 'A', 'UA80000000000093317',
                    50.45, 30.52, 'B', 'UA80000000000093317', 50.46, 30.53, 10, 2, 'EXPRESS',
                    50, 1.5, 10, 15, 20, 1.5, 127.5, now(), now() + interval '15 minutes')
                """);

        flyway(dataSource, null).migrate();
    }

    @Test
    void backfillsSenderAndDropsOrphanRecords() {
        assertThat(jdbc.queryForList("SELECT idempotency_key || ':' || user_id FROM idempotency_records", String.class))
                .containsExactly("k:42");
    }

    @Test
    void sameKeyIsUniquePerSenderOnly() {
        jdbc.update("INSERT INTO idempotency_records (user_id, idempotency_key, shipment_id, created_at) VALUES (43, 'k', 1, now())");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO idempotency_records (user_id, idempotency_key, shipment_id, created_at) VALUES (42, 'k', 1, now())"))
                .hasMessageContaining("uq_idempotency_sender_key");
    }

    @Test
    void removesRequestHashAndPreservesExistingKeys() {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'idempotency_records' AND column_name = 'request_hash'
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT shipment_id FROM idempotency_records WHERE user_id = 42 AND idempotency_key = 'k'", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void removesQuoteExpirationWithoutChangingSavedPrice() {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'price_quotes' AND column_name = 'expires_at'
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT total_price FROM price_quotes", java.math.BigDecimal.class))
                .isEqualByComparingTo("127.5");
    }

    @Test
    void removesDeliveryTypesWithoutChangingStoredPrices() {
        assertThat(jdbc.queryForObject("SELECT price FROM shipments WHERE shipment_id = 1", java.math.BigDecimal.class))
                .isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject("SELECT total_price FROM price_quotes", java.math.BigDecimal.class))
                .isEqualByComparingTo("127.5");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name IN ('shipments', 'price_quotes')
                  AND column_name IN ('delivery_type', 'delivery_type_multiplier')
                """, Integer.class)).isZero();
    }

    private static Flyway flyway(DriverManagerDataSource dataSource, String target) {
        var config = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration");
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }
}
