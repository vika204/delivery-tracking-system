package com.delivery.tracking.client;

import com.delivery.tracking.client.dto.CreateShipmentRequest;
import com.delivery.tracking.client.dto.ShipmentBatchResponse;
import com.delivery.tracking.client.dto.ShipmentDto;
import com.delivery.tracking.service.TrackingService;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ShipmentClientIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tracking_test_db")
            .withUsername("test_user")
            .withPassword("test_pass");

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("shipment.client.base-url", wireMock::baseUrl);
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

        registry.add("resilience4j.retry.instances.shipmentClient.wait-duration", () -> "10ms");
        registry.add("resilience4j.retry.instances.shipmentClient.enable-randomized-wait", () -> "false");
    }

    @Autowired
    private TrackingService trackingService;

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
    }

    @Test
    void shouldReturnShipment_andPassCorrelationId_whenShipmentServiceIsOk() {
        wireMock.stubFor(get(urlEqualTo("/shipments/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "shipmentId": 1,
                                  "recipientName": "John Doe",
                                  "status": "CREATED"
                                }
                                """)));

        ShipmentDto result = trackingService.getShipmentById(1L);

        assertThat(result.shipmentId()).isEqualTo(1L);
        assertThat(result.recipientName()).isEqualTo("John Doe");

        wireMock.verify(1, getRequestedFor(urlEqualTo("/shipments/1"))
                .withHeader("X-Correlation-Id", matching(".*")));
    }

    @Test
    void shouldFallback_whenShipmentServiceFails() {
        wireMock.stubFor(get(urlEqualTo("/shipments/2"))
                .willReturn(aResponse().withStatus(500)));

        ShipmentDto result = trackingService.getShipmentById(2L);

        assertThat(result.status()).isEqualTo("UNAVAILABLE");
        assertThat(result.recipientName()).contains("Fallback");
    }

    @Test
    void shouldRetry_whenShipmentServiceFails() {
        wireMock.stubFor(post(urlPathEqualTo("/shipments"))
                .willReturn(aResponse().withStatus(500)));

        CreateShipmentRequest request = new CreateShipmentRequest(1L, "R", "P", "A", "B", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, "CREATED");
        String key = UUID.randomUUID().toString();
        
        try {
            trackingService.createShipment(key, request);
        } catch (Exception ignored) {
        }

        wireMock.verify(moreThan(1), postRequestedFor(urlPathEqualTo("/shipments")));
    }

    @Test
    void shouldOpenCircuitBreaker_whenThresholdIsReached() {
        wireMock.stubFor(get(urlEqualTo("/shipments/3"))
                .willReturn(aResponse().withStatus(500)));

        for (int i = 0; i < 10; i++) {
            trackingService.getShipmentById(3L);
        }

        wireMock.resetRequests();

        ShipmentDto result = trackingService.getShipmentById(3L);
        assertThat(result.status()).isEqualTo("UNAVAILABLE");

        wireMock.verify(0, getRequestedFor(urlEqualTo("/shipments/3")));
    }

    @Test
    void shouldHandleProblemDetail() {
        wireMock.stubFor(get(urlPathMatching("/shipments/batch.*"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("""
                                {
                                  "type": "urn:delivery:problem:shipment-not-found",
                                  "title": "Shipment not found",
                                  "status": 404,
                                  "detail": "Shipment 99 not found"
                                }
                                """)));

        assertThatThrownBy(() -> trackingService.getShipmentsBatch(List.of(99L)))
                .isInstanceOf(HttpClientErrorException.NotFound.class)
                .hasMessageContaining("404");
    }

    @Test
    void shouldReturnBatch() {
        wireMock.stubFor(get(urlPathMatching("/shipments/batch.*"))
                .withQueryParam("ids", containing("10"))
                .withQueryParam("ids", containing("11"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "shipments": [
                                    {"shipmentId": 10, "status": "CREATED"},
                                    {"shipmentId": 11, "status": "DELIVERED"}
                                  ],
                                  "missingIds": []
                                }
                                """)));

        ShipmentBatchResponse result = trackingService.getShipmentsBatch(List.of(10L, 11L));
        assertThat(result.shipments()).hasSize(2);
        assertThat(result.missingIds()).isEmpty();
    }

    @Test
    void shouldPassIdempotencyKeyOnCreate() {
        wireMock.stubFor(post(urlEqualTo("/shipments"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "shipmentId": 100,
                                  "status": "CREATED"
                                }
                                """)));

        CreateShipmentRequest request = new CreateShipmentRequest(1L, "R", "P", "A", "B", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, "CREATED");
        String key = UUID.randomUUID().toString();
        trackingService.createShipment(key, request);

        wireMock.verify(1, postRequestedFor(urlEqualTo("/shipments"))
                .withHeader("Idempotency-Key", equalTo(key)));
    }
}
