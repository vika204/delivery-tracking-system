package com.delivery.tracking.client;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Chaos experiments for the tracking-service -> shipment-service hop. WireMock plays a shipment-service that resets
 * connections, hangs up, sends garbage, answers too slowly or recovers. Steady state: tracking answers every request
 * quickly with a clean, typed error (never a hang, a stack trace or a thread leak), stops hammering a sick dependency
 * (circuit breaker) and heals by itself once the dependency is healthy again.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.grpc.server.port=0",
        "resilience4j.retry.instances.shipmentClient.wait-duration=20ms",
        "resilience4j.retry.instances.shipmentClient.enable-randomized-wait=false",
        "resilience4j.circuitbreaker.instances.shipmentClient.wait-duration-in-open-state=800ms",
        "resilience4j.circuitbreaker.instances.shipmentClient.permitted-number-of-calls-in-half-open-state=2"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class ShipmentClientChaosIntegrationTest {

    private static final String HEALTHY_BODY = """
            {"shipmentId": 1, "recipientName": "Marta Shevchenko", "status": "WAITING_FOR_COURIER"}
            """;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @RegisterExtension
    static WireMockExtension shipment = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("shipment.client.base-url", shipment::baseUrl);
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private MockMvc mvc;
    @Autowired private CircuitBreakerRegistry circuitBreakers;

    @BeforeEach
    void healthyWorld() {
        shipment.resetAll();
        circuitBreakers.circuitBreaker("shipmentClient").reset();
        shipment.stubFor(get(urlEqualTo("/shipments/1")).willReturn(healthy()));
    }

    // ---- transport-level faults --------------------------------------------------------------------------

    @Test
    void connectionResetByPeerDegradesToTheFallbackAnswer() throws Exception {
        shipment.stubFor(get(urlEqualTo("/shipments/1")).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        MvcResult result = timed(() -> mvc.perform(MockMvcRequestBuilders.get("/tracking/shipments/1")).andReturn(), Duration.ofSeconds(10));

        assertDegraded(result);
        shipment.verify(3, getRequestedFor(urlEqualTo("/shipments/1"))); // retried, then given up
    }

    @Test
    void shipmentServiceHangingUpWithoutAnswerDegradesToTheFallbackAnswer() throws Exception {
        shipment.stubFor(get(urlEqualTo("/shipments/1")).willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));

        assertDegraded(timed(() -> mvc.perform(MockMvcRequestBuilders.get("/tracking/shipments/1")).andReturn(), Duration.ofSeconds(10)));
    }

    @Test
    void aResponseCutInTheMiddleDegradesToTheFallbackAnswer() throws Exception {
        shipment.stubFor(get(urlEqualTo("/shipments/1")).willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));

        assertDegraded(timed(() -> mvc.perform(MockMvcRequestBuilders.get("/tracking/shipments/1")).andReturn(), Duration.ofSeconds(10)));
    }

    @Test
    void garbageOnTheWireNeverLeaksAStackTraceToTheCaller() throws Exception {
        shipment.stubFor(get(urlEqualTo("/shipments/1")).willReturn(aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE)));

        MvcResult result = timed(() -> mvc.perform(MockMvcRequestBuilders.get("/tracking/shipments/1")).andReturn(), Duration.ofSeconds(10));

        assertDegraded(result);
        assertNoLeak(result);
    }

    @Test
    void aBodyThatIsNotJsonDoesNotLeakAStackTraceEither() throws Exception {
        shipment.stubFor(get(urlEqualTo("/shipments/1")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json").withBody("<html>proxy error</html>")));

        MvcResult result = timed(() -> mvc.perform(MockMvcRequestBuilders.get("/tracking/shipments/1")).andReturn(), Duration.ofSeconds(10));

        assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(500);
        assertNoLeak(result);
    }

    @Test
    void aShipmentServiceSlowerThanTheReadTimeoutIsCutOffInsteadOfHangingTheCaller() throws Exception {
        shipment.stubFor(get(urlEqualTo("/shipments/1")).willReturn(healthy().withFixedDelay(4_500))); // read timeout is 3 s

        MvcResult result = timed(() -> mvc.perform(MockMvcRequestBuilders.get("/tracking/shipments/1")).andReturn(), Duration.ofSeconds(20));

        assertDegraded(result);
    }

    @Test
    void endpointsWithoutAFallbackReportACleanServiceUnavailable() throws Exception {
        shipment.stubFor(get(urlEqualTo("/shipments/batch?ids=1")).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        MvcResult batch = timed(() -> mvc.perform(MockMvcRequestBuilders.get("/tracking/shipments/batch").param("ids", "1")).andReturn(),
                Duration.ofSeconds(10));

        assertCleanUnavailable(batch);
    }

    @Test
    void aSlowButWithinTimeoutShipmentServiceIsStillServed() throws Exception {
        shipment.stubFor(get(urlEqualTo("/shipments/1")).willReturn(healthy().withFixedDelay(800)));

        MvcResult result = mvc.perform(MockMvcRequestBuilders.get("/tracking/shipments/1")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        shipment.verify(1, getRequestedFor(urlEqualTo("/shipments/1")));
    }

    // ---- retry / circuit breaker discipline ---------------------------------------------------------------

    @Test
    void clientErrorsAreNeverRetried() throws Exception {
        shipment.stubFor(get(urlEqualTo("/shipments/1")).willReturn(aResponse().withStatus(404)));

        MvcResult result = mvc.perform(MockMvcRequestBuilders.get("/tracking/shipments/1")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        shipment.verify(1, getRequestedFor(urlEqualTo("/shipments/1")));
        assertThat(circuitBreakers.circuitBreaker("shipmentClient").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void aDeadShipmentServiceTripsTheBreakerAndTrackingStopsCallingIt() throws Exception {
        shipment.stubFor(get(urlEqualTo("/shipments/1")).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        for (int i = 0; i < 6; i++) {
            mvc.perform(MockMvcRequestBuilders.get("/tracking/shipments/1"));
        }
        assertThat(circuitBreakers.circuitBreaker("shipmentClient").getState()).isEqualTo(CircuitBreaker.State.OPEN);

        shipment.resetRequests();
        long started = System.nanoTime();
        MvcResult shortCircuited = mvc.perform(MockMvcRequestBuilders.get("/tracking/shipments/1")).andReturn();
        long tookMs = Duration.ofNanos(System.nanoTime() - started).toMillis();

        assertThat(tookMs).as("an open breaker answers immediately").isLessThan(500);
        shipment.verify(0, getRequestedFor(urlEqualTo("/shipments/1")));
        assertDegraded(shortCircuited);
    }

    @Test
    void trackingHealsByItselfOnceShipmentServiceIsBack() throws Exception {
        shipment.stubFor(get(urlEqualTo("/shipments/1")).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        for (int i = 0; i < 6; i++) {
            mvc.perform(MockMvcRequestBuilders.get("/tracking/shipments/1"));
        }
        assertThat(circuitBreakers.circuitBreaker("shipmentClient").getState()).isEqualTo(CircuitBreaker.State.OPEN);

        shipment.stubFor(get(urlEqualTo("/shipments/1")).willReturn(healthy()));

        // the breaker only changes state when a call arrives, so keep calling until real data comes back
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(300)).untilAsserted(() -> {
            MvcResult answer = mvc.perform(MockMvcRequestBuilders.get("/tracking/shipments/1")).andReturn();
            assertThat(answer.getResponse().getStatus()).isEqualTo(200);
            assertThat(answer.getResponse().getContentAsString()).contains("WAITING_FOR_COURIER");
        });
        mvc.perform(MockMvcRequestBuilders.get("/tracking/shipments/1"));
        assertThat(circuitBreakers.circuitBreaker("shipmentClient").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void aFlappingShipmentServiceDoesNotTripTheBreakerBelowTheFailureThreshold() throws Exception {
        shipment.stubFor(get(urlEqualTo("/shipments/1")).willReturn(healthy()));
        for (int i = 0; i < 8; i++) {
            mvc.perform(MockMvcRequestBuilders.get("/tracking/shipments/1"));
        }
        shipment.stubFor(get(urlEqualTo("/shipments/1")).willReturn(aResponse().withStatus(503)));
        mvc.perform(MockMvcRequestBuilders.get("/tracking/shipments/1")); // one failed call (3 attempts) in a window of mostly successes

        assertThat(circuitBreakers.circuitBreaker("shipmentClient").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    // ---- load while the dependency is sick ----------------------------------------------------------------

    @Test
    void aBurstOfCallersAgainstASlowShipmentServiceAllReturnInBoundedTime() throws Exception {
        shipment.stubFor(get(urlEqualTo("/shipments/1")).willReturn(healthy().withFixedDelay(1_500)));
        int callers = 40;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                results.add(pool.submit(() -> mvc.perform(MockMvcRequestBuilders.get("/tracking/shipments/1")).andReturn().getResponse().getStatus()));
            }
            int ok = 0;
            int shed = 0;
            for (Future<Integer> result : results) {
                int status = result.get(30, java.util.concurrent.TimeUnit.SECONDS);
                if (status == 200) {
                    ok++;
                } else {
                    assertThat(status).as("shed load is reported as unavailable, not as a crash").isEqualTo(503);
                    shed++;
                }
            }
            assertThat(ok + shed).isEqualTo(callers);
            assertThat(ok).as("the bulkhead (20 concurrent) still lets work through").isGreaterThan(0);
            assertThat(shed).as("40 simultaneous callers exceed the 20-call bulkhead, so some are shed fast").isGreaterThan(0);
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- helpers --------------------------------------------------------------------------------------------

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder healthy() {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(HEALTHY_BODY);
    }

    private static void assertCleanUnavailable(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        assertThat(result.getResponse().getContentAsString()).contains("urn:delivery:problem:shipment-unavailable");
        assertNoLeak(result);
    }

    /** Graceful degradation: the single-shipment lookup answers 200 with an explicit UNAVAILABLE marker. */
    private static void assertDegraded(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).contains("\"status\":\"UNAVAILABLE\"");
        assertNoLeak(result);
    }

    private static void assertNoLeak(MvcResult result) throws Exception {
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("Exception", "at com.", "at java.", "org.springframework", "localhost:");
    }

    private interface Call {
        MvcResult run() throws Exception;
    }

    private static MvcResult timed(Call call, Duration limit) throws Exception {
        long started = System.nanoTime();
        MvcResult result = call.run();
        Duration took = Duration.ofNanos(System.nanoTime() - started);
        assertThat(took).as("the caller must not hang on a faulty dependency").isLessThan(limit);
        return result;
    }
}
