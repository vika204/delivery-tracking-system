package com.delivery.shipment.chaos;

import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import com.delivery.shipment.entity.PriceQuote;
import com.delivery.shipment.entity.Shipment;
import com.delivery.shipment.entity.ShipmentStatus;
import com.delivery.shipment.repository.OutboxEventRepository;
import com.delivery.shipment.repository.PriceQuoteRepository;
import com.delivery.shipment.repository.ShipmentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.toxiproxy.ToxiproxyContainer;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Chaos experiments for shipment-service. Postgres sits behind Toxiproxy, so the network between the service and its
 * database can be slowed down or cut while the real application runs. Steady state for every experiment: reads work,
 * and a shipment is either fully created (row + outbox event + consumed quote) or not at all.
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=localhost:9999",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false",
        "outbox.relay.enabled=false",
        "app.chaos.enabled=true",
        "spring.datasource.hikari.connection-timeout=2000",
        "spring.datasource.hikari.maximum-pool-size=4",
        "spring.datasource.hikari.keepalive-time=0"
})
@AutoConfigureMockMvc
@Testcontainers
class ShipmentChaosIntegrationTest {

    private static final int PROXY_PORT = 8666;
    private static final UUID QUOTE_ID = UUID.fromString("7d444840-9dc0-11d1-b245-5ffdce74fad2");

    static final Network network = Network.newNetwork();

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
            .withNetwork(network)
            .withNetworkAliases("postgres");

    @Container
    static ToxiproxyContainer toxiproxy = new ToxiproxyContainer("ghcr.io/shopify/toxiproxy:2.12.0")
            .withNetwork(network);

    static Proxy dbProxy;

    @BeforeAll
    static void createProxy() throws IOException {
        ToxiproxyClient client = new ToxiproxyClient(toxiproxy.getHost(), toxiproxy.getControlPort());
        dbProxy = client.createProxy("postgres", "0.0.0.0:" + PROXY_PORT, "postgres:5432");
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://%s:%d/%s?connectTimeout=2&socketTimeout=5"
                .formatted(toxiproxy.getHost(), toxiproxy.getMappedPort(PROXY_PORT), postgres.getDatabaseName()));
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private MockMvc mvc;
    @Autowired private ShipmentRepository shipments;
    @Autowired private PriceQuoteRepository quotes;
    @Autowired private OutboxEventRepository outbox;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() throws Exception {
        dbProxy.enable();
        dbProxy.toxics().getAll().forEach(toxic -> {
            try {
                toxic.remove();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
        mvc.perform(delete("/internal/chaos"));
        jdbc.execute("TRUNCATE TABLE shipments, shipment_status_history, price_quotes, idempotency_records, "
                + "outbox_events, processed_events RESTART IDENTITY CASCADE");
    }

    @AfterEach
    void restoreNetwork() throws Exception {
        dbProxy.enable();
        mvc.perform(delete("/internal/chaos"));
    }

    // ---- network faults between the service and its database ----------------------------------------------

    @Test
    void slowDatabaseSlowsReadsDownButDoesNotBreakThem() throws Exception {
        long id = savedShipment();
        dbProxy.toxics().latency("db-latency", ToxicDirection.DOWNSTREAM, 600).setJitter(0);

        long started = System.nanoTime();
        mvc.perform(get("/shipments/" + id)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        long tookMs = Duration.ofNanos(System.nanoTime() - started).toMillis();

        assertThat(tookMs).as("a 600 ms network delay must be visible, proving the fault was injected").isGreaterThanOrEqualTo(600);
        assertThat(tookMs).as("but the request must stay bounded").isLessThan(15_000);
    }

    @Test
    void severedDatabaseFailsFastAndTheServiceRecoversWithoutRestart() throws Exception {
        long id = savedShipment();
        mvc.perform(get("/shipments/" + id)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

        dbProxy.disable();

        long started = System.nanoTime();
        MvcResult failed = mvc.perform(get("/shipments/" + id)).andReturn();
        long tookMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
        assertThat(failed.getResponse().getStatus()).as("a dead database is a server-side failure").isGreaterThanOrEqualTo(500);
        assertThat(tookMs).as("and it must fail fast, not hang").isLessThan(15_000);
        assertThat(failed.getResponse().getContentAsString()).as("no stack trace leaks to the caller").doesNotContain("Exception", "at com.delivery");

        dbProxy.enable();

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500)).untilAsserted(() ->
                assertThat(mvc.perform(get("/shipments/" + id)).andReturn().getResponse().getStatus()).isEqualTo(200));
    }

    @Test
    void hungDatabaseConnectionIsCutByTheSocketTimeout() throws Exception {
        long id = savedShipment();
        // "timeout" toxic with 0 swallows data and never answers: the classic half-open connection.
        dbProxy.toxics().timeout("db-hang", ToxicDirection.DOWNSTREAM, 0);

        long started = System.nanoTime();
        int status = mvc.perform(get("/shipments/" + id)).andReturn().getResponse().getStatus();
        long tookMs = Duration.ofNanos(System.nanoTime() - started).toMillis();

        assertThat(status).isGreaterThanOrEqualTo(500);
        assertThat(tookMs).as("socketTimeout=5s must release the request thread").isLessThan(20_000);

        dbProxy.toxics().get("db-hang").remove();
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500)).untilAsserted(() ->
                assertThat(mvc.perform(get("/shipments/" + id)).andReturn().getResponse().getStatus()).isEqualTo(200));
    }

    @Test
    void databaseOutageDuringCreationLeavesNoPartialStateAndRetryWithSameKeySucceeds() throws Exception {
        seedQuote();
        dbProxy.disable();

        int failed = create("chaos-key").getResponse().getStatus();
        assertThat(failed).isGreaterThanOrEqualTo(500);

        dbProxy.enable();
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT count(*) FROM shipments", Long.class)).isZero());
        assertThat(outbox.count()).as("no event for a shipment that was never created").isZero();
        assertThat(quotes.findById(QUOTE_ID).orElseThrow().getShipmentId()).as("quote is not consumed").isNull();

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500)).untilAsserted(() ->
                assertThat(create("chaos-key").getResponse().getStatus()).isEqualTo(200));
        assertThat(shipments.count()).isEqualTo(1);
        assertThat(outbox.count()).as("exactly one creation event").isEqualTo(1);
    }

    // ---- application-level fault injection (app.chaos.enabled) -------------------------------------------------

    @Test
    void injectedFailuresRejectCreationWithoutSideEffectsAndResetHeals() throws Exception {
        seedQuote();
        mvc.perform(post("/internal/chaos/fail").param("rate", "1"));

        MvcResult failed = create("injected-key");
        assertThat(failed.getResponse().getStatus()).isEqualTo(500);
        assertThat(shipments.count()).isZero();
        assertThat(outbox.count()).isZero();
        assertThat(quotes.findById(QUOTE_ID).orElseThrow().getShipmentId()).isNull();

        mvc.perform(delete("/internal/chaos"));

        assertThat(create("injected-key").getResponse().getStatus()).isEqualTo(200);
        assertThat(shipments.count()).isEqualTo(1);
    }

    @Test
    void injectedLatencyDelaysCreationButNeverCorruptsIt() throws Exception {
        seedQuote();
        mvc.perform(post("/internal/chaos/latency").param("ms", "700"));

        long started = System.nanoTime();
        MvcResult created = create("slow-key");
        long tookMs = Duration.ofNanos(System.nanoTime() - started).toMillis();

        assertThat(created.getResponse().getStatus()).isEqualTo(200);
        assertThat(tookMs).isGreaterThanOrEqualTo(700);
        assertThat(shipments.count()).isEqualTo(1);
        assertThat(outbox.count()).isEqualTo(1);
    }

    @Test
    void partialFailureRateStillKeepsEveryCreatedShipmentConsistent() throws Exception {
        mvc.perform(post("/internal/chaos/fail").param("rate", "0.5"));
        int attempts = 20;
        int succeeded = 0;
        for (int i = 0; i < attempts; i++) {
            UUID quote = UUID.randomUUID();
            seedQuote(quote);
            if (create("flaky-" + i, quote).getResponse().getStatus() == 200) {
                succeeded++;
            }
        }

        assertThat(shipments.count()).as("one shipment per successful call, none for failed ones").isEqualTo(succeeded);
        assertThat(outbox.count()).as("one event per shipment").isEqualTo(succeeded);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM price_quotes WHERE shipment_id IS NOT NULL", Long.class))
                .as("a quote is consumed exactly when its shipment exists").isEqualTo(succeeded);
        assertThat(succeeded).as("with a 50% injected failure rate both outcomes must occur").isBetween(1, attempts - 1);
    }

    // ---- helpers --------------------------------------------------------------------------------------------

    private MvcResult create(String idempotencyKey) throws Exception {
        return create(idempotencyKey, QUOTE_ID);
    }

    private MvcResult create(String idempotencyKey, UUID quoteId) throws Exception {
        return mvc.perform(post("/shipments")
                .header("X-User-Id", 42)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"quoteId": "%s", "senderName": "Olena Kovalenko", "senderPhone": "+380501234567",
                         "recipientName": "Marta Shevchenko", "recipientPhone": "+380671234567",
                         "length": 30, "width": 20, "height": 15}
                        """.formatted(quoteId))).andReturn();
    }

    private long savedShipment() {
        return shipments.saveAndFlush(Shipment.builder()
                .userId(42L)
                .senderName("Olena Kovalenko").senderPhone("+380501234567")
                .recipientName("Marta Shevchenko").recipientPhone("+380671234567")
                .pickupAddress("Kyiv, Khreshchatyk 1").pickupCityCode("UA80000000000093317")
                .pickupLatitude(50.4501).pickupLongitude(30.5234)
                .deliveryAddress("Lviv, Svobody 10").deliveryCityCode("UA46060250010015970")
                .deliveryLatitude(49.8397).deliveryLongitude(24.0297)
                .weight(new BigDecimal("2.50")).length(new BigDecimal("30.00"))
                .width(new BigDecimal("20.00")).height(new BigDecimal("15.00"))
                .distance(new BigDecimal("540.00")).price(new BigDecimal("885.00"))
                .status(ShipmentStatus.WAITING_FOR_COURIER)
                .build()).getShipmentId();
    }

    private void seedQuote() {
        seedQuote(QUOTE_ID);
    }

    private void seedQuote(UUID quoteId) {
        Instant now = Instant.now();
        quotes.saveAndFlush(PriceQuote.builder()
                .quoteId(quoteId).userId(42L)
                .pickupAddress("Kyiv, Khreshchatyk 1").pickupCityCode("UA80000000000093317")
                .pickupLatitude(50.4501).pickupLongitude(30.5234)
                .deliveryAddress("Lviv, Svobody 10").deliveryCityCode("UA46060250010015970")
                .deliveryLatitude(49.8397).deliveryLongitude(24.0297)
                .distanceKm(new BigDecimal("540.00")).weight(new BigDecimal("2.50"))
                .basePrice(new BigDecimal("50.00")).pricePerKm(new BigDecimal("1.50")).pricePerKg(new BigDecimal("10.00"))
                .distanceCharge(new BigDecimal("810.00")).weightCharge(new BigDecimal("25.00"))
                .totalPrice(new BigDecimal("885.00"))
                .calculatedAt(now)
                .build());
    }
}
