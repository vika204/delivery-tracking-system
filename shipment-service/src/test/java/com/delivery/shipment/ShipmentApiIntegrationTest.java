package com.delivery.shipment;

import com.delivery.shipment.entity.OutboxEvent;
import com.delivery.shipment.entity.ShipmentStatus;
import com.delivery.shipment.event.DispatchEvent;
import com.delivery.shipment.event.ShipmentCreatedEvent;
import com.delivery.shipment.event.ShipmentDetailsUpdatedEvent;
import com.delivery.shipment.event.ShipmentStatusChangedEvent;
import com.delivery.shipment.exception.InvalidDispatchEventException;
import com.delivery.shipment.geo.OfflineRoutingClient;
import com.delivery.shipment.geo.RoutingClient;
import com.delivery.shipment.kafka.DispatchEventListener;
import com.delivery.shipment.outbox.OutboxRelay;
import com.delivery.shipment.outbox.OutboxTraceContext;
import com.delivery.shipment.repository.IdempotencyRecordRepository;
import com.delivery.shipment.repository.OutboxEventRepository;
import com.delivery.shipment.repository.PriceQuoteRepository;
import com.delivery.shipment.repository.ProcessedEventRepository;
import com.delivery.shipment.repository.ShipmentRepository;
import com.delivery.shipment.repository.ShipmentStatusHistoryRepository;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.common.KafkaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=localhost:9999",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false",
        "outbox.relay.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers
class ShipmentApiIntegrationTest {

    static final String KYIV = "UA80000000000093317";
    static final String LVIV = "UA46060250010015970";
    static final long SENDER = 42;
    static final long COURIER = 7;

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private DispatchEventListener dispatchListener;
    @Autowired private ShipmentRepository shipments;
    @Autowired private PriceQuoteRepository quotes;
    @MockitoSpyBean private OutboxEventRepository outbox;
    @Autowired private IdempotencyRecordRepository idempotency;
    @Autowired private ShipmentStatusHistoryRepository history;
    @Autowired private ProcessedEventRepository processed;
    @Autowired private OutboxTraceContext traceContext;
    @Autowired private MeterRegistry meterRegistry;

    @TestBean private RoutingClient routingClient;

    static RoutingClient routingClient() {
        return new OfflineRoutingClient();
    }

    @BeforeEach
    void cleanDatabase() {
        outbox.deleteAll();
        idempotency.deleteAll();
        history.deleteAll();
        processed.deleteAll();
        shipments.deleteAll();
        quotes.deleteAll();
    }

    @Test
    void quoteShowsDistanceAndPriceBreakdown() throws Exception {
        JsonNode quote = quote(SENDER, "2.5");

        assertThat(quote.get("distanceKm").asDouble()).isBetween(400.0, 750.0);
        JsonNode breakdown = quote.get("breakdown");
        assertThat(breakdown.get("basePrice").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(breakdown.get("weightCharge").decimalValue()).isEqualByComparingTo("25.00");
        assertThat(breakdown.has("deliveryTypeMultiplier")).isFalse();
        assertThat(quote.get("totalPrice").decimalValue()).isEqualByComparingTo(breakdown.get("total").decimalValue());
        assertThat(quote.get("pickup").get("latitude").asDouble()).isBetween(50.0, 51.0);
    }

    @Test
    void quoteUsesOnePriceFormula() throws Exception {
        JsonNode result = quote(SENDER, "2.5");
        java.math.BigDecimal expected = new java.math.BigDecimal("50.00")
                .add(result.get("distanceKm").decimalValue().multiply(new java.math.BigDecimal("1.50")))
                .add(new java.math.BigDecimal("25.00"))
                .setScale(2, java.math.RoundingMode.HALF_UP);

        assertThat(result.get("totalPrice").decimalValue()).isEqualByComparingTo(expected);
        assertThat(result.get("breakdown").has("deliveryType")).isFalse();
        assertThat(result.get("breakdown").has("subtotal")).isFalse();
    }

    @Test
    void quoteWithUnknownSettlementIsRejected() throws Exception {
        mockMvc.perform(post("/shipments/quotes").header("X-User-Id", SENDER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteJson("UA99000000000000000", "1")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:delivery:problem:address-not-geocodable"));
    }

    @Test
    void quoteRequiresUser() throws Exception {
        mockMvc.perform(post("/shipments/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteJson(LVIV, "1")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createsShipmentFromQuoteWithAgreedPriceAndEvent() throws Exception {
        JsonNode quote = quote(SENDER, "2.5");
        double registeredBefore = registered();

        JsonNode shipment = json(create(SENDER, quote, "key-1").andExpect(status().isOk()));

        assertThat(shipment.get("status").asText()).isEqualTo("WAITING_FOR_COURIER");
        assertThat(shipment.get("price").decimalValue()).isEqualByComparingTo(quote.get("totalPrice").decimalValue());
        assertThat(shipment.get("distance").decimalValue()).isEqualByComparingTo(quote.get("distanceKm").decimalValue());
        assertThat(shipment.has("deliveryType")).isFalse();
        assertThat(shipment.get("userId").asLong()).isEqualTo(SENDER);
        assertThat(registered()).isEqualTo(registeredBefore + 1);

        ShipmentCreatedEvent event = onlyEvent(ShipmentCreatedEvent.TYPE, ShipmentCreatedEvent.class);
        assertThat(event.payload().shipmentId()).isEqualTo(shipment.get("shipmentId").asLong());
        assertThat(event.payload().pickupCityCode()).isEqualTo(KYIV);
        assertThat(event.payload().pickupLatitude()).isNotNull();
        assertThat(event.payload().weight()).isEqualByComparingTo("2.5");
        assertThat(jsonMapper.valueToTree(event.payload()).has("deliveryType")).isFalse();

        mockMvc.perform(get("/shipments/" + id(shipment) + "/history"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].toStatus").value("WAITING_FOR_COURIER"))
                .andExpect(jsonPath("$[0].actorType").value("SENDER"));
    }

    @Test
    void replayWithSameKeyReturnsSameShipment() throws Exception {
        JsonNode quote = quote(SENDER, "1");
        long first = id(json(create(SENDER, quote, "key-replay")));

        create(SENDER, quote, "key-replay")
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.shipmentId").value(first));
        assertThat(shipments.count()).isEqualTo(1);
    }

    @Test
    void sameKeyWithDifferentDataIsRejected() throws Exception {
        JsonNode first = quote(SENDER, "1");
        JsonNode second = quote(SENDER, "1");
        create(SENDER, first, "key-changed").andExpect(status().isOk());

        create(SENDER, second, "key-changed")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:delivery:problem:idempotency-key-reuse"));
        assertThat(shipments.count()).isEqualTo(1);
    }

    @Test
    void sameKeyOfAnotherSenderCreatesSeparateShipment() throws Exception {
        long first = id(json(create(SENDER, quote(SENDER, "1"), "shared-key").andExpect(status().isOk())));

        long second = id(json(create(43, quote(43, "1"), "shared-key")
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Idempotent-Replayed"))));

        assertThat(second).isNotEqualTo(first);
        assertThat(events(ShipmentCreatedEvent.TYPE, ShipmentCreatedEvent.class)).hasSize(2);
    }

    @Test
    void replayStillReturnsShipmentAfterQuoteExpired() throws Exception {
        JsonNode quote = quote(SENDER, "1");
        long first = id(json(create(SENDER, quote, "key-late-retry")));
        var stored = quotes.findById(UUID.fromString(quote.get("quoteId").asText())).orElseThrow();
        stored.setExpiresAt(Instant.now().minusSeconds(60));
        quotes.save(stored);

        create(SENDER, quote, "key-late-retry")
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.shipmentId").value(first));
    }

    @Test
    void concurrentRetriesWithSameKeyCreateOneShipment() throws Exception {
        JsonNode quote = quote(SENDER, "1");
        CyclicBarrier start = new CyclicBarrier(2);

        List<CompletableFuture<MockHttpServletResponse>> attempts = List.of(1, 2).stream()
                .map(attempt -> CompletableFuture.supplyAsync(() -> {
                    try {
                        start.await();
                        return create(SENDER, quote, "key-double-click").andReturn().getResponse();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }))
                .toList();
        List<MockHttpServletResponse> responses = attempts.stream().map(CompletableFuture::join).toList();

        assertThat(responses).allSatisfy(response -> assertThat(response.getStatus()).isEqualTo(200));
        assertThat(responses).filteredOn(response -> "true".equals(response.getHeader("Idempotent-Replayed"))).hasSize(1);
        assertThat(responses.stream().map(this::shipmentId).distinct()).hasSize(1);
        assertThat(shipments.count()).isEqualTo(1);
        assertThat(events(ShipmentCreatedEvent.TYPE, ShipmentCreatedEvent.class)).hasSize(1);
    }

    @Test
    void quoteCannotBeUsedTwice() throws Exception {
        JsonNode quote = quote(SENDER, "1");
        create(SENDER, quote, "key-a").andExpect(status().isOk());

        create(SENDER, quote, "key-b")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:delivery:problem:quote-already-used"));
    }

    @Test
    void expiredQuoteIsRejected() throws Exception {
        JsonNode quote = quote(SENDER, "1");
        var stored = quotes.findById(UUID.fromString(quote.get("quoteId").asText())).orElseThrow();
        stored.setExpiresAt(Instant.now().minusSeconds(1));
        quotes.save(stored);

        create(SENDER, quote, "key-expired")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:delivery:problem:quote-expired"));
        assertThat(shipments.count()).isZero();
    }

    @Test
    void anotherUsersQuoteLooksMissing() throws Exception {
        JsonNode quote = quote(SENDER, "1");

        create(99, quote, "key-foreign")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:delivery:problem:quote-not-found"));
    }

    @Test
    void createRejectsInvalidRecipientPhone() throws Exception {
        JsonNode quote = quote(SENDER, "1");

        mockMvc.perform(post("/shipments").header("X-User-Id", SENDER).header("Idempotency-Key", "k")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(quote).replace("+380671234567", "not a phone")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("recipientPhone"));
    }

    @Test
    void failedOutboxWriteRollsBackShipment() throws Exception {
        JsonNode quote = quote(SENDER, "1");
        doThrow(new IllegalStateException("Outbox insert failed")).when(outbox).save(any(OutboxEvent.class));

        create(SENDER, quote, "key-rollback").andExpect(status().isInternalServerError());

        assertThat(shipments.count()).isZero();
        assertThat(history.count()).isZero();
        assertThat(quotes.findById(UUID.fromString(quote.get("quoteId").asText())).orElseThrow().getShipmentId()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void outboxEventStaysPendingWhenKafkaFails() throws Exception {
        long shipmentId = id(json(create(SENDER, quote(SENDER, "1"), "key-kafka-down")));
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(eq("shipment.events"), eq(String.valueOf(shipmentId)), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new KafkaException("Kafka unavailable")));

        new OutboxRelay(outbox, kafka, new SimpleMeterRegistry(), traceContext, java.time.Clock.systemUTC()).publishPendingEvents();

        OutboxEvent event = outbox.findAll().getFirst();
        assertThat(event.isPublished()).isFalse();
        assertThat(event.getPublishedAt()).isNull();
    }

    @Test
    void courierAssignedEventAssignsCourierOnce() throws Exception {
        long shipmentId = createShipment();
        String event = dispatchEvent(DispatchEvent.COURIER_ASSIGNED, shipmentId, COURIER, null);

        dispatchListener.onDispatchEvent(event);
        dispatchListener.onDispatchEvent(event);

        mockMvc.perform(get("/shipments/" + shipmentId))
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignedCourierId").value(COURIER))
                .andExpect(jsonPath("$.assignedCourierName").value("Andrii Kovalenko"));

        List<ShipmentStatusChangedEvent> changes = events(ShipmentStatusChangedEvent.TYPE, ShipmentStatusChangedEvent.class);
        assertThat(changes).hasSize(1);
        assertThat(changes.getFirst().status()).isEqualTo("ASSIGNED");
        assertThat(changes.getFirst().previousStatus()).isEqualTo("WAITING_FOR_COURIER");
        assertThat(changes.getFirst().courierId()).isEqualTo(COURIER);
    }

    @Test
    void statusEventCarriesDataForNotificationAndTracking() throws Exception {
        long shipmentId = assignedShipment();

        ShipmentStatusChangedEvent assigned = onlyEvent(ShipmentStatusChangedEvent.TYPE, ShipmentStatusChangedEvent.class);
        assertThat(assigned.senderId()).isEqualTo(SENDER);
        assertThat(assigned.courierName()).isEqualTo("Andrii Kovalenko");
        assertThat(assigned.pickupLatitude()).isBetween(50.0, 51.0);
        assertThat(assigned.deliveryLongitude()).isNotNull();

        outbox.deleteAll();
        cancel(SENDER, shipmentId);
        ShipmentStatusChangedEvent cancelled = onlyEvent(ShipmentStatusChangedEvent.TYPE, ShipmentStatusChangedEvent.class);
        assertThat(cancelled.courierId()).isEqualTo(COURIER);
        assertThat(cancelled.courierName()).isEqualTo("Andrii Kovalenko");
    }

    @Test
    void shipmentResponseExposesCoordinates() throws Exception {
        long shipmentId = createShipment();

        mockMvc.perform(get("/shipments/" + shipmentId))
                .andExpect(jsonPath("$.pickupLatitude").isNumber())
                .andExpect(jsonPath("$.pickupLongitude").isNumber())
                .andExpect(jsonPath("$.deliveryLatitude").isNumber())
                .andExpect(jsonPath("$.deliveryLongitude").isNumber());
    }

    @Test
    void searchDelayedEventDoesNotChangeShipment() throws Exception {
        long shipmentId = createShipment();
        String delayed = jsonMapper.writeValueAsString(new DispatchEvent(UUID.randomUUID(),
                DispatchEvent.COURIER_SEARCH_DELAYED, Instant.now(), shipmentId, null, null, null,
                "no available couriers", SENDER, Instant.now().minusSeconds(300)));

        dispatchListener.onDispatchEvent(delayed);

        assertThat(shipments.findById(shipmentId).orElseThrow().getStatus()).isEqualTo(ShipmentStatus.WAITING_FOR_COURIER);
        assertThat(processed.count()).isZero();
    }

    @Test
    void courierAssignedAfterCancellationIsIgnored() throws Exception {
        long shipmentId = createShipment();
        cancel(SENDER, shipmentId).andExpect(status().isOk());

        dispatchListener.onDispatchEvent(dispatchEvent(DispatchEvent.COURIER_ASSIGNED, shipmentId, COURIER, null));

        assertThat(shipments.findById(shipmentId).orElseThrow().getStatus()).isEqualTo(ShipmentStatus.CANCELLED);
    }

    @Test
    void courierUnassignedReturnsShipmentToWaiting() throws Exception {
        long shipmentId = assignedShipment();

        dispatchListener.onDispatchEvent(dispatchEvent(DispatchEvent.COURIER_UNASSIGNED, shipmentId, COURIER, "vehicle breakdown"));

        mockMvc.perform(get("/shipments/" + shipmentId))
                .andExpect(jsonPath("$.status").value("WAITING_FOR_COURIER"))
                .andExpect(jsonPath("$.assignedCourierId").doesNotExist());
        mockMvc.perform(get("/shipments/" + shipmentId + "/history"))
                .andExpect(jsonPath("$[2].toStatus").value("WAITING_FOR_COURIER"))
                .andExpect(jsonPath("$[2].reason").value("vehicle breakdown"));
    }

    @Test
    void malformedDispatchEventIsNotRetried() {
        assertThatThrownBy(() -> dispatchListener.onDispatchEvent("{not json"))
                .isInstanceOf(InvalidDispatchEventException.class);
        assertThatThrownBy(() -> dispatchListener.onDispatchEvent("{\"eventType\":\"COURIER_ASSIGNED\"}"))
                .isInstanceOf(InvalidDispatchEventException.class);
    }

    @Test
    void courierCompletesDeliveryAndTimelineIsRecorded() throws Exception {
        long shipmentId = assignedShipment();

        courier(COURIER, shipmentId, "start-trip").andExpect(jsonPath("$.status").value("TO_PICKUP"));
        courier(COURIER, shipmentId, "pickup").andExpect(jsonPath("$.status").value("IN_TRANSIT"));
        courier(COURIER, shipmentId, "deliver")
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.deliveredAt").isNotEmpty());

        mockMvc.perform(get("/shipments/" + shipmentId + "/history"))
                .andExpect(jsonPath("$[*].toStatus").value(org.hamcrest.Matchers.contains(
                        "WAITING_FOR_COURIER", "ASSIGNED", "TO_PICKUP", "IN_TRANSIT", "DELIVERED")));
        assertThat(events(ShipmentStatusChangedEvent.TYPE, ShipmentStatusChangedEvent.class))
                .extracting(ShipmentStatusChangedEvent::status)
                .containsExactly("ASSIGNED", "TO_PICKUP", "IN_TRANSIT", "DELIVERED");
    }

    @Test
    void courierActionsAreValidated() throws Exception {
        long shipmentId = assignedShipment();

        courier(99, shipmentId, "pickup").andExpect(status().isForbidden());
        courier(COURIER, shipmentId, "deliver")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:delivery:problem:shipment-status-conflict"));
    }

    @Test
    void courierViewShowsInitialsAndPhones() throws Exception {
        long shipmentId = assignedShipment();

        mockMvc.perform(get("/shipments/" + shipmentId + "/courier-view").header("X-User-Id", COURIER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sender.initials").value("O. K."))
                .andExpect(jsonPath("$.sender.phone").value("+380501234567"))
                .andExpect(jsonPath("$.recipient.initials").value("M. S."))
                .andExpect(jsonPath("$.recipient.phone").value("+380671234567"));

        mockMvc.perform(get("/shipments/" + shipmentId + "/courier-view").header("X-User-Id", 99))
                .andExpect(status().isForbidden());
    }

    @Test
    void senderCancelsAssignedShipmentAndCourierIsNotified() throws Exception {
        long shipmentId = assignedShipment();

        cancel(SENDER, shipmentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("plans changed"));

        ShipmentStatusChangedEvent cancelled = events(ShipmentStatusChangedEvent.TYPE, ShipmentStatusChangedEvent.class).getLast();
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.courierId()).isEqualTo(COURIER);
        assertThat(cancelled.actorType()).isEqualTo("SENDER");
    }

    @Test
    void cancellationAfterPickupIsRejected() throws Exception {
        long shipmentId = assignedShipment();
        courier(COURIER, shipmentId, "pickup");

        cancel(SENDER, shipmentId).andExpect(status().isConflict());
    }

    @Test
    void onlySenderCanCancelAndRepeatIsNoOp() throws Exception {
        long shipmentId = createShipment();

        cancel(99, shipmentId).andExpect(status().isForbidden());
        cancel(SENDER, shipmentId).andExpect(status().isOk());
        cancel(SENDER, shipmentId).andExpect(status().isOk());

        assertThat(events(ShipmentStatusChangedEvent.TYPE, ShipmentStatusChangedEvent.class)).hasSize(1);
    }

    @Test
    void contactChangeKeepsPrice() throws Exception {
        long shipmentId = createShipment();
        var before = shipments.findById(shipmentId).orElseThrow().getPrice();

        edit(SENDER, shipmentId, "{\"recipientName\": \"Ivan Bondarenko\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceRecalculated").value(false))
                .andExpect(jsonPath("$.shipment.recipientName").value("Ivan Bondarenko"))
                .andExpect(jsonPath("$.shipment.price").value(before.doubleValue()));
    }

    @Test
    void routeChangeRecalculatesPriceAndNotifiesDispatch() throws Exception {
        long shipmentId = createShipment();
        var before = shipments.findById(shipmentId).orElseThrow();

        JsonNode result = json(edit(SENDER, shipmentId, """
                {"deliveryAddress": "Saksahanskoho 28", "deliveryCityCode": "%s", "weight": 4}
                """.formatted(KYIV)).andExpect(status().isOk()));

        assertThat(result.get("priceRecalculated").asBoolean()).isTrue();
        assertThat(result.get("previousPrice").decimalValue()).isEqualByComparingTo(before.getPrice());
        assertThat(result.get("shipment").get("distance").decimalValue()).isLessThan(before.getDistance());
        assertThat(result.get("shipment").get("price").decimalValue())
                .isEqualByComparingTo(result.get("breakdown").get("total").decimalValue());

        ShipmentDetailsUpdatedEvent event = onlyEvent(ShipmentDetailsUpdatedEvent.TYPE, ShipmentDetailsUpdatedEvent.class);
        assertThat(event.priceRecalculated()).isTrue();
        assertThat(event.payload().deliveryCityCode()).isEqualTo(KYIV);
        assertThat(event.payload().weight()).isEqualByComparingTo("4");
    }

    @Test
    void editIsForbiddenAfterAssignment() throws Exception {
        long shipmentId = assignedShipment();

        edit(SENDER, shipmentId, "{\"weight\": 3}").andExpect(status().isConflict());
    }

    @Test
    void editValidatesAddressPairAndEmptyBody() throws Exception {
        long shipmentId = createShipment();

        edit(SENDER, shipmentId, "{\"deliveryAddress\": \"Saksahanskoho 28\"}").andExpect(status().isBadRequest());
        edit(SENDER, shipmentId, "{}").andExpect(status().isBadRequest());
        edit(99, shipmentId, "{\"weight\": 3}").andExpect(status().isForbidden());
    }

    @Test
    void myShipmentsAreFilteredAndNewestFirst() throws Exception {
        long first = createShipment();
        long second = createShipment();
        long cancelled = createShipment();
        cancel(SENDER, cancelled);
        createShipmentFor(77);

        mockMvc.perform(get("/shipments/mine").header("X-User-Id", SENDER))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].shipmentId").value(cancelled))
                .andExpect(jsonPath("$[2].shipmentId").value(first));

        mockMvc.perform(get("/shipments/mine").header("X-User-Id", SENDER).param("statusGroup", "ACTIVE"))
                .andExpect(jsonPath("$[*].shipmentId").value(org.hamcrest.Matchers.contains(
                        (int) second, (int) first)));

        mockMvc.perform(get("/shipments/mine").header("X-User-Id", SENDER).param("statusGroup", "CANCELLED"))
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/shipments/mine").header("X-User-Id", SENDER).param("createdFrom", "2000-01-01")
                        .param("createdTo", "2000-12-31"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    private JsonNode quote(long userId, String weight) throws Exception {
        return json(mockMvc.perform(post("/shipments/quotes").header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteJson(LVIV, weight)))
                .andExpect(status().isCreated()));
    }

    private static String quoteJson(String deliveryCode, String weight) {
        return """
                {"pickupAddress": "Khreshchatyk 1", "pickupCityCode": "%s",
                 "deliveryAddress": "Svobody 10", "deliveryCityCode": "%s",
                 "weight": %s}
                """.formatted(KYIV, deliveryCode, weight);
    }

    private ResultActions create(long userId, JsonNode quote, String key) throws Exception {
        return mockMvc.perform(post("/shipments").header("X-User-Id", userId).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(createJson(quote)));
    }

    private static String createJson(JsonNode quote) {
        return """
                {"quoteId": "%s", "senderName": "Olena Kovalenko", "senderPhone": "+380501234567",
                 "recipientName": "Marta Shevchenko", "recipientPhone": "+380671234567",
                 "length": 30, "width": 20, "height": 15}
                """.formatted(quote.get("quoteId").asText());
    }

    private long createShipment() throws Exception {
        return createShipmentFor(SENDER);
    }

    private long createShipmentFor(long userId) throws Exception {
        long id = id(json(create(userId, quote(userId, "2"), UUID.randomUUID().toString())
                .andExpect(status().isOk())));
        outbox.deleteAll();
        return id;
    }

    private long assignedShipment() throws Exception {
        long shipmentId = createShipment();
        dispatchListener.onDispatchEvent(dispatchEvent(DispatchEvent.COURIER_ASSIGNED, shipmentId, COURIER, null));
        return shipmentId;
    }

    private String dispatchEvent(String type, long shipmentId, long courierId, String reason) {
        return jsonMapper.writeValueAsString(new DispatchEvent(UUID.randomUUID(), type, Instant.now(),
                shipmentId, courierId, "Andrii Kovalenko", 1L, reason, SENDER, null));
    }

    private ResultActions courier(long courierId, long shipmentId, String action) throws Exception {
        return mockMvc.perform(post("/shipments/" + shipmentId + "/" + action).header("X-User-Id", courierId));
    }

    private ResultActions cancel(long userId, long shipmentId) throws Exception {
        return mockMvc.perform(post("/shipments/" + shipmentId + "/cancel").header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"plans changed\"}"));
    }

    private ResultActions edit(long userId, long shipmentId, String body) throws Exception {
        return mockMvc.perform(patch("/shipments/" + shipmentId).header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private long shipmentId(MockHttpServletResponse response) {
        try {
            return id(jsonMapper.readTree(response.getContentAsString()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static long id(JsonNode shipment) {
        return shipment.get("shipmentId").asLong();
    }

    private <T> List<T> events(String type, Class<T> eventClass) {
        return outbox.findAll().stream()
                .filter(e -> e.getEventType().equals(type))
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .map(e -> jsonMapper.readValue(e.getPayload(), eventClass))
                .toList();
    }

    private <T> T onlyEvent(String type, Class<T> eventClass) {
        List<T> found = events(type, eventClass);
        assertThat(found).hasSize(1);
        return found.getFirst();
    }

    private double registered() {
        return meterRegistry.get("delivery.shipments.registered").counter().count();
    }
}
