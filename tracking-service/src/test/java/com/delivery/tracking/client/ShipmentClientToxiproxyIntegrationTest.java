package com.delivery.tracking.client;

import com.delivery.tracking.service.TrackingService;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "resilience4j.retry.instances.shipmentClient.wait-duration=10ms",
        "resilience4j.circuitbreaker.instances.shipmentClient.wait-duration-in-open-state=1s"
})
@Testcontainers
@DirtiesContext
class ShipmentClientToxiproxyIntegrationTest {

    static final Network network = Network.newNetwork();

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> shipment = new GenericContainer<>("wiremock/wiremock:3.12.0")
            .withNetwork(network)
            .withNetworkAliases("shipment-stub")
            .withExposedPorts(8080);

    @Container
    static GenericContainer<?> toxiproxy = new GenericContainer<>("ghcr.io/shopify/toxiproxy:2.13.0")
            .withNetwork(network)
            .withExposedPorts(8474, 8666)
            .withCommand("-host=0.0.0.0")
            .waitingFor(Wait.forHttp("/version").forPort(8474));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("shipment.client.base-url", () -> "http://" + toxiproxy.getHost() + ":" + toxiproxy.getMappedPort(8666));
    }

    @Autowired
    TrackingService trackingService;

    @Autowired
    CircuitBreakerRegistry breakers;

    @Autowired
    MeterRegistry metrics;

    RestClient proxyAdmin;

    @BeforeEach
    void prepare() {
        proxyAdmin = RestClient.create("http://" + toxiproxy.getHost() + ":" + toxiproxy.getMappedPort(8474));
        proxyAdmin.post().uri("/populate")
                .body(List.of(Map.of("name", "shipment", "listen", "0.0.0.0:8666",
                        "upstream", "shipment-stub:8080", "enabled", true)))
                .retrieve().toBodilessEntity();
        breakers.circuitBreaker("shipmentClient").reset();
        WireMock upstream = new WireMock(shipment.getHost(), shipment.getMappedPort(8080));
        upstream.resetMappings();
        upstream.register(get(urlEqualTo("/shipments/15"))
                .willReturn(okJson("""
                        {"shipmentId":15,"recipientName":"Test recipient","status":"ASSIGNED"}
                        """)));
        assertThat(trackingService.getShipmentByIdWithoutFallback(15L).status()).isEqualTo("ASSIGNED");
    }

    @AfterEach
    void restoreConnection() {
        proxyAdmin.post().uri("/reset").retrieve().toBodilessEntity();
    }

    @Test
    void latencyCausesThreeAttemptsAndFallbackThenRecovers() {
        var calls = metrics.get("delivery.tracking.shipment.client").tag("operation", "get").timer();
        var fallbacks = metrics.get("delivery.tracking.fallbacks").counter();
        long attemptsBefore = calls.count();
        double fallbacksBefore = fallbacks.count();
        proxyAdmin.post().uri("/proxies/shipment/toxics")
                .body(Map.of("name", "latency", "type", "latency", "stream", "downstream",
                        "toxicity", 1.0, "attributes", Map.of("latency", 5000, "jitter", 0)))
                .retrieve().toBodilessEntity();

        assertThat(trackingService.getShipmentById(15L).status()).isEqualTo("UNAVAILABLE");
        assertThat(calls.count() - attemptsBefore).isEqualTo(3);
        assertThat(fallbacks.count() - fallbacksBefore).isEqualTo(1);

        proxyAdmin.delete().uri("/proxies/shipment/toxics/latency").retrieve().toBodilessEntity();
        assertThat(trackingService.getShipmentById(15L).status()).isEqualTo("ASSIGNED");
    }

    @Test
    void brokenConnectionOpensBreakerAndRestoredConnectionClosesIt() {
        CircuitBreaker breaker = breakers.circuitBreaker("shipmentClient");
        List<CircuitBreaker.StateTransition> transitions = new ArrayList<>();
        breaker.getEventPublisher().onStateTransition(event -> transitions.add(event.getStateTransition()));
        proxyAdmin.post().uri("/proxies/shipment").body(Map.of("enabled", false)).retrieve().toBodilessEntity();

        for (int request = 0; request < 10 && breaker.getState() != CircuitBreaker.State.OPEN; request++) {
            assertThat(trackingService.getShipmentById(15L).status()).isEqualTo("UNAVAILABLE");
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        var calls = metrics.get("delivery.tracking.shipment.client").tag("operation", "get").timer();
        long attemptsBefore = calls.count();
        assertThat(trackingService.getShipmentById(15L).status()).isEqualTo("UNAVAILABLE");
        assertThat(calls.count()).isEqualTo(attemptsBefore);

        proxyAdmin.post().uri("/proxies/shipment").body(Map.of("enabled", true)).retrieve().toBodilessEntity();
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
            assertThat(trackingService.getShipmentById(15L).status()).isEqualTo("ASSIGNED");
            assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        });
        assertThat(transitions).containsSubsequence(
                CircuitBreaker.StateTransition.CLOSED_TO_OPEN,
                CircuitBreaker.StateTransition.OPEN_TO_HALF_OPEN,
                CircuitBreaker.StateTransition.HALF_OPEN_TO_CLOSED);
    }
}
