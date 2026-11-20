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
class RemoveToPickupMigrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private JdbcTemplate jdbc;

    @BeforeEach
    void migrateDataWithToPickup() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
        flyway(dataSource, "3").migrate();

        shipment(1, "TO_PICKUP");
        shipment(2, "IN_TRANSIT");
        history(1, "WAITING_FOR_COURIER", "ASSIGNED");
        history(1, "ASSIGNED", "TO_PICKUP");
        history(2, "WAITING_FOR_COURIER", "ASSIGNED");
        history(2, "ASSIGNED", "TO_PICKUP");
        history(2, "TO_PICKUP", "IN_TRANSIT");

        flyway(dataSource, null).migrate();
    }

    @Test
    void shipmentOnTheWayToPickupBecomesAssigned() {
        assertThat(jdbc.queryForObject("SELECT status FROM shipments WHERE shipment_id = 1", String.class))
                .isEqualTo("ASSIGNED");
        assertThat(jdbc.queryForObject("SELECT status FROM shipments WHERE shipment_id = 2", String.class))
                .isEqualTo("IN_TRANSIT");
    }

    @Test
    void timelineGoesFromAssignedStraightToInTransit() {
        assertThat(jdbc.queryForList(
                "SELECT coalesce(from_status, '-') || '>' || to_status FROM shipment_status_history WHERE shipment_id = 2 ORDER BY history_id",
                String.class))
                .containsExactly("WAITING_FOR_COURIER>ASSIGNED", "ASSIGNED>IN_TRANSIT");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM shipment_status_history WHERE 'TO_PICKUP' IN (from_status, to_status)", Integer.class))
                .isZero();
    }

    @Test
    void toPickupIsNoLongerAllowedAndTripStartIsGone() {
        assertThatThrownBy(() -> jdbc.update("UPDATE shipments SET status = 'TO_PICKUP' WHERE shipment_id = 1"))
                .hasMessageContaining("shipments_status_check");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_name = 'shipments' AND column_name = 'trip_started_at'",
                Integer.class))
                .isZero();
    }

    private void shipment(long id, String status) {
        jdbc.update("""
                INSERT INTO shipments (shipment_id, user_id, recipient_name, recipient_phone, pickup_address,
                    delivery_address, weight, length, width, height, distance, price, status, trip_started_at, created_at)
                VALUES (?, 42, 'Marta', '+380671234567', 'A', 'B', 1, 1, 1, 1, 10, 100, ?, now(), now())
                """, id, status);
    }

    private void history(long shipmentId, String from, String to) {
        jdbc.update("""
                INSERT INTO shipment_status_history (shipment_id, from_status, to_status, changed_at, actor_type)
                VALUES (?, ?, ?, now(), 'SYSTEM')
                """, shipmentId, from, to);
    }

    private static Flyway flyway(DriverManagerDataSource dataSource, String target) {
        var config = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration");
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }
}
