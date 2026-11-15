package com.delivery.dispatch.service;

import com.delivery.dispatch.entity.Assignment;
import com.delivery.dispatch.entity.AssignmentStatus;
import com.delivery.dispatch.entity.Courier;
import com.delivery.dispatch.entity.DeliveryOffer;
import com.delivery.dispatch.entity.DispatchRequestStatus;
import com.delivery.dispatch.entity.OfferStatus;
import com.delivery.dispatch.entity.OutboxEvent;
import com.delivery.dispatch.events.DispatchEvent;
import com.delivery.dispatch.events.ShipmentEventMessage;
import com.delivery.dispatch.events.ShipmentSnapshot;
import com.delivery.dispatch.events.ShipmentStatusChangedMessage;
import com.delivery.dispatch.exception.DispatchConflictException;
import com.delivery.dispatch.kafka.ShipmentEventProcessor;
import com.delivery.dispatch.repository.AssignmentRepository;
import com.delivery.dispatch.repository.CourierRepository;
import com.delivery.dispatch.repository.DeliveryOfferRepository;
import com.delivery.dispatch.repository.DispatchRequestRepository;
import com.delivery.dispatch.repository.OutboxEventRepository;
import com.delivery.dispatch.repository.ProcessedEventRepository;
import com.delivery.dispatch.scheduling.DispatchQueueProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Сценарії Dispatch на реальному Postgres: пошук, пропозиції, призначення, відмови, скасування.
 * Kafka-споживачі та планувальники вимкнені: події подаються через {@link ShipmentEventProcessor},
 * а тайм-аути пропозицій — через {@link DispatchQueueProcessor#expireOffers()}.
 */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.kafka.bootstrap-servers=localhost:9999",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false",
        "outbox.relay.enabled=false",
        "dispatch.scheduling.enabled=false",
        "dispatch.max-active-assignments=2"
})
@AutoConfigureMockMvc
@Testcontainers
class DispatchFlowIntegrationTest {

    static final String KYIV = "UA80000000000093317";
    static final String KYIV_OBLAST = "UA32020090010010446";
    static final String LVIV = "UA46060250010015970";

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private ShipmentEventProcessor events;
    @Autowired private DispatchService dispatchService;
    @Autowired private DispatchQueueProcessor queue;
    @Autowired private CourierRepository couriers;
    @Autowired private DeliveryOfferRepository offers;
    @Autowired private AssignmentRepository assignments;
    @Autowired private DispatchRequestRepository requests;
    @Autowired private OutboxEventRepository outbox;
    @Autowired private ProcessedEventRepository processed;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private MockMvc mockMvc;

    private long nextShipmentId = 1000;

    @BeforeEach
    void cleanDatabase() {
        offers.deleteAll();
        assignments.deleteAll();
        requests.deleteAll();
        couriers.deleteAll();
        outbox.deleteAll();
        processed.deleteAll();
    }

    // ---------------------------------------------------------------- пошук

    @Test
    void offersShipmentToNearestEligibleCourier() {
        courier(1, KYIV, "20", true, 50.40, 30.50);   // ~6 km — найближчий, але офлайн
        courier(2, KYIV, "1", true, 50.45, 30.52);    // поруч, але не підніме 5 кг
        courier(3, LVIV, "20", true, 50.45, 30.52);   // інша зона
        courier(4, KYIV, "20", true, 50.50, 30.60);   // ~10 km
        courier(5, KYIV, "20", true, 50.46, 30.53);   // ~1.5 km — переможець
        offline(1);

        long shipmentId = created(KYIV, "5", "STANDARD");

        DeliveryOffer offer = pendingOffer(shipmentId);
        assertThat(offer.getCourier().getUserId()).isEqualTo(5L);
        assertThat(offer.getDistanceToPickupKm()).isLessThan(5.0);
        assertThat(request(shipmentId)).isEqualTo(DispatchRequestStatus.OFFERED);
    }

    @Test
    void courierZoneCoversKyivCityAndOblast() {
        courier(1, KYIV_OBLAST, "20", true, null, null);

        long shipmentId = created(KYIV, "5", "STANDARD");

        assertThat(pendingOffer(shipmentId).getCourier().getUserId()).isEqualTo(1L);
    }

    @Test
    void courierHasAtMostOnePendingOffer() {
        courier(1, KYIV, "20", true, null, null);

        long first = created(KYIV, "1", "STANDARD");
        long second = created(KYIV, "1", "STANDARD");

        assertThat(pendingOffer(first).getCourier().getUserId()).isEqualTo(1L);
        assertThat(offers.findPendingByCourier(1L)).hasSize(1);
        assertThat(request(second)).isEqualTo(DispatchRequestStatus.WAITING_FOR_COURIERS);
    }

    @Test
    void shipmentWaitsWithoutCouriersAndIsOfferedWhenCourierGoesOnline() throws Exception {
        courier(1, KYIV, "20", false, null, null);

        long shipmentId = created(KYIV, "2", "STANDARD");
        assertThat(request(shipmentId)).isEqualTo(DispatchRequestStatus.WAITING_FOR_COURIERS);

        mockMvc.perform(put("/couriers/1/availability")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"online\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.online").value(true));

        // після коміту CourierCapacityFreedEvent роздає чергу очікування
        assertThat(pendingOffer(shipmentId).getCourier().getUserId()).isEqualTo(1L);
    }

    @Test
    void expressShipmentIsServedFirstFromWaitingQueue() {
        long standard = created(KYIV, "1", "STANDARD");
        long express = created(KYIV, "1", "EXPRESS");
        courier(1, KYIV, "20", true, null, null);

        queue.processQueue();

        assertThat(pendingOffer(express).getCourier().getUserId()).isEqualTo(1L);
        assertThat(request(standard)).isEqualTo(DispatchRequestStatus.WAITING_FOR_COURIERS);
    }

    @Test
    void longSearchPublishesDelayOnce() {
        long shipmentId = created(KYIV, "1", "STANDARD");
        assertThat(outbox.count()).as("no delay right after creation").isZero();

        var request = requests.findById(shipmentId).orElseThrow();
        request.setCreatedAt(Instant.now().minusSeconds(600));
        requests.save(request);

        queue.processQueue();
        queue.processQueue();

        DispatchEvent delayed = lastDispatchEvent();
        assertThat(outbox.count()).isEqualTo(1);
        assertThat(delayed.eventType()).isEqualTo(DispatchEvent.COURIER_SEARCH_DELAYED);
        assertThat(delayed.shipmentId()).isEqualTo(shipmentId);
        assertThat(delayed.senderId()).isEqualTo(42L);
        assertThat(delayed.courierId()).isNull();
        assertThat(delayed.waitingSince()).isBefore(Instant.now().minusSeconds(500));
    }

    @Test
    void courierAtCapacityGetsNoNewOffers() {
        courier(1, KYIV, "20", true, null, null);
        acceptCurrent(created(KYIV, "1", "STANDARD"), 1);
        acceptCurrent(created(KYIV, "1", "STANDARD"), 1);

        long third = created(KYIV, "1", "STANDARD");

        assertThat(request(third)).isEqualTo(DispatchRequestStatus.WAITING_FOR_COURIERS);
    }

    // ---------------------------------------------------------------- відповідь на пропозицію

    @Test
    void acceptingOfferAssignsCourierAndPublishesCourierAssigned() throws Exception {
        courier(1, KYIV, "20", true, null, null);
        long shipmentId = created(KYIV, "3", "STANDARD");
        long offerId = pendingOffer(shipmentId).getOfferId();

        mockMvc.perform(post("/couriers/1/offers/" + offerId + "/accept").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipmentId").value(shipmentId))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(request(shipmentId)).isEqualTo(DispatchRequestStatus.ASSIGNED);
        assertThat(offers.findById(offerId).orElseThrow().getStatus()).isEqualTo(OfferStatus.ACCEPTED);

        DispatchEvent event = lastDispatchEvent();
        assertThat(event.eventType()).isEqualTo(DispatchEvent.COURIER_ASSIGNED);
        assertThat(event.shipmentId()).isEqualTo(shipmentId);
        assertThat(event.courierId()).isEqualTo(1L);
        assertThat(event.courierName()).isEqualTo("Courier 1");
    }

    @Test
    void acceptIsIdempotentForSameCourier() {
        courier(1, KYIV, "20", true, null, null);
        long shipmentId = created(KYIV, "3", "STANDARD");
        long offerId = pendingOffer(shipmentId).getOfferId();

        Assignment first = dispatchService.acceptOffer(1, offerId);
        Assignment second = dispatchService.acceptOffer(1, offerId);

        assertThat(second.getAssignmentId()).isEqualTo(first.getAssignmentId());
        assertThat(outbox.count()).isEqualTo(1);
    }

    @Test
    void decliningOfferProposesShipmentToNextCandidate() {
        courier(1, KYIV, "20", true, 50.45, 30.52);
        courier(2, KYIV, "20", true, 50.55, 30.70);
        long shipmentId = created(KYIV, "3", "STANDARD");
        long firstOffer = pendingOffer(shipmentId).getOfferId();

        dispatchService.declineOffer(1, firstOffer);

        assertThat(offers.findById(firstOffer).orElseThrow().getStatus()).isEqualTo(OfferStatus.REJECTED);
        assertThat(pendingOffer(shipmentId).getCourier().getUserId()).isEqualTo(2L);
    }

    @Test
    void declinedCourierIsNotOfferedTheSameShipmentAgain() {
        courier(1, KYIV, "20", true, null, null);
        long shipmentId = created(KYIV, "3", "STANDARD");

        dispatchService.declineOffer(1, pendingOffer(shipmentId).getOfferId());
        queue.processQueue();

        assertThat(offers.findPendingByCourier(1L)).isEmpty();
        assertThat(request(shipmentId)).isEqualTo(DispatchRequestStatus.WAITING_FOR_COURIERS);
    }

    @Test
    void unansweredOfferExpiresAndGoesToNextCandidate() {
        courier(1, KYIV, "20", true, 50.45, 30.52);
        courier(2, KYIV, "20", true, 50.55, 30.70);
        long shipmentId = created(KYIV, "3", "STANDARD");
        DeliveryOffer offer = pendingOffer(shipmentId);
        expireNow(offer);

        queue.expireOffers();

        assertThat(offers.findById(offer.getOfferId()).orElseThrow().getStatus()).isEqualTo(OfferStatus.EXPIRED);
        assertThat(pendingOffer(shipmentId).getCourier().getUserId()).isEqualTo(2L);
    }

    @Test
    void expiredOfferCannotBeAccepted() {
        courier(1, KYIV, "20", true, null, null);
        long shipmentId = created(KYIV, "3", "STANDARD");
        DeliveryOffer offer = pendingOffer(shipmentId);
        expireNow(offer);

        assertThatThrownBy(() -> dispatchService.acceptOffer(1, offer.getOfferId()))
                .isInstanceOf(DispatchConflictException.class)
                .hasMessageContaining("expired");
        assertThat(assignments.count()).isZero();
    }

    @Test
    void courierCannotAnswerAnotherCouriersOffer() throws Exception {
        courier(1, KYIV, "20", true, null, null);
        courier(2, KYIV, "20", false, null, null);
        long offerId = pendingOffer(created(KYIV, "3", "STANDARD")).getOfferId();

        mockMvc.perform(post("/couriers/2/offers/" + offerId + "/accept").header("X-User-Id", "2"))
                .andExpect(status().isForbidden());
    }

    @Test
    void currentOfferEndpointShowsRouteAndReward() throws Exception {
        courier(1, KYIV, "20", true, null, null);
        long shipmentId = created(KYIV, "3", "EXPRESS");

        mockMvc.perform(get("/couriers/1/offers/current").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipmentId").value(shipmentId))
                .andExpect(jsonPath("$.pickupAddress").value("Khreshchatyk 1"))
                .andExpect(jsonPath("$.price").value(120.5))
                .andExpect(jsonPath("$.deliveryType").value("EXPRESS"));

        mockMvc.perform(get("/couriers/2/offers/current").header("X-User-Id", "1"))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- відмова від призначення

    @Test
    void withdrawalBeforePickupUnassignsAndSearchesWithoutThatCourier() {
        courier(1, KYIV, "20", true, 50.45, 30.52);
        courier(2, KYIV, "20", true, 50.55, 30.70);
        long shipmentId = created(KYIV, "3", "STANDARD");
        Assignment assignment = acceptCurrent(shipmentId, 1);
        events.process(statusEvent(shipmentId, "ASSIGNED", 1L));

        dispatchService.withdrawAssignment(1, assignment.getAssignmentId(), "vehicle breakdown");

        assertThat(assignments.findById(assignment.getAssignmentId()).orElseThrow().getStatus())
                .isEqualTo(AssignmentStatus.COURIER_WITHDREW);
        DispatchEvent unassigned = lastDispatchEvent();
        assertThat(unassigned.eventType()).isEqualTo(DispatchEvent.COURIER_UNASSIGNED);
        assertThat(unassigned.reason()).isEqualTo("vehicle breakdown");
        assertThat(pendingOffer(shipmentId).getCourier().getUserId()).isEqualTo(2L);
    }

    @Test
    void withdrawalAfterPickupIsRejected() {
        courier(1, KYIV, "20", true, null, null);
        long shipmentId = created(KYIV, "3", "STANDARD");
        Assignment assignment = acceptCurrent(shipmentId, 1);
        events.process(statusEvent(shipmentId, "IN_TRANSIT", 1L));

        assertThatThrownBy(() -> dispatchService.withdrawAssignment(1, assignment.getAssignmentId(), null))
                .isInstanceOf(DispatchConflictException.class)
                .hasMessageContaining("picked up");
        assertThat(request(shipmentId)).isEqualTo(DispatchRequestStatus.IN_PROGRESS);
    }

    @Test
    void pickupReportedAfterWithdrawalRevertsWithdrawal() {
        courier(1, KYIV, "20", true, 50.45, 30.52);
        courier(2, KYIV, "20", true, 50.55, 30.70);
        long shipmentId = created(KYIV, "3", "STANDARD");
        Assignment assignment = acceptCurrent(shipmentId, 1);
        dispatchService.withdrawAssignment(1, assignment.getAssignmentId(), "late withdrawal");
        DeliveryOffer replacement = pendingOffer(shipmentId);

        // Shipment встиг зафіксувати забір посилки першим кур'єром
        events.process(statusEvent(shipmentId, "IN_TRANSIT", 1L));

        Assignment restored = assignments.findById(assignment.getAssignmentId()).orElseThrow();
        assertThat(restored.getStatus()).isEqualTo(AssignmentStatus.ACTIVE);
        assertThat(restored.getPickedUpAt()).isNotNull();
        assertThat(offers.findById(replacement.getOfferId()).orElseThrow().getStatus()).isEqualTo(OfferStatus.CANCELLED);
        assertThat(request(shipmentId)).isEqualTo(DispatchRequestStatus.IN_PROGRESS);
    }

    // ---------------------------------------------------------------- скасування й завершення

    @Test
    void cancellationAbortsSearchAndCancelsPendingOffer() {
        courier(1, KYIV, "20", true, null, null);
        long shipmentId = created(KYIV, "3", "STANDARD");
        long offerId = pendingOffer(shipmentId).getOfferId();

        events.process(statusEvent(shipmentId, "CANCELLED", null));

        assertThat(offers.findById(offerId).orElseThrow().getStatus()).isEqualTo(OfferStatus.CANCELLED);
        assertThat(request(shipmentId)).isEqualTo(DispatchRequestStatus.CANCELLED);
    }

    @Test
    void cancellationAfterAssignmentReleasesCourierForWaitingShipment() {
        courier(1, KYIV, "20", true, null, null);
        dispatchCapacity(1);
        long assigned = created(KYIV, "3", "STANDARD");
        Assignment assignment = acceptCurrent(assigned, 1);
        long waiting = created(KYIV, "3", "STANDARD");
        assertThat(request(waiting)).isEqualTo(DispatchRequestStatus.WAITING_FOR_COURIERS);

        events.process(statusEvent(assigned, "CANCELLED", 1L));

        assertThat(assignments.findById(assignment.getAssignmentId()).orElseThrow().getStatus())
                .isEqualTo(AssignmentStatus.CANCELLED);
        assertThat(pendingOffer(waiting).getCourier().getUserId()).isEqualTo(1L);
    }

    @Test
    void deliveryCompletesAssignment() {
        courier(1, KYIV, "20", true, null, null);
        long shipmentId = created(KYIV, "3", "STANDARD");
        Assignment assignment = acceptCurrent(shipmentId, 1);
        events.process(statusEvent(shipmentId, "IN_TRANSIT", 1L));

        events.process(statusEvent(shipmentId, "DELIVERED", 1L));

        assertThat(assignments.findById(assignment.getAssignmentId()).orElseThrow().getStatus())
                .isEqualTo(AssignmentStatus.COMPLETED);
        assertThat(request(shipmentId)).isEqualTo(DispatchRequestStatus.COMPLETED);
    }

    @Test
    void cancellationArrivingBeforeCreationLeavesTombstone() {
        courier(1, KYIV, "20", true, null, null);
        long shipmentId = nextShipmentId++;

        events.process(statusEvent(shipmentId, "CANCELLED", null));
        events.process(createdEvent(shipmentId, KYIV, "1", "STANDARD"));

        assertThat(request(shipmentId)).isEqualTo(DispatchRequestStatus.CANCELLED);
        assertThat(offers.count()).isZero();
    }

    @Test
    void detailsUpdateRestartsSearchWithNewSnapshot() {
        courier(1, KYIV, "5", true, 50.45, 30.52);
        courier(2, KYIV, "50", true, 50.55, 30.70);
        long shipmentId = created(KYIV, "3", "STANDARD");
        long firstOffer = pendingOffer(shipmentId).getOfferId();

        // відправник збільшив вагу: першому кур'єру вже завелика
        events.process(new ShipmentEventMessage(UUID.randomUUID(), ShipmentEventMessage.SHIPMENT_DETAILS_UPDATED,
                Instant.now(), snapshot(shipmentId, KYIV, "20", "STANDARD")));

        assertThat(offers.findById(firstOffer).orElseThrow().getStatus()).isEqualTo(OfferStatus.CANCELLED);
        assertThat(pendingOffer(shipmentId).getCourier().getUserId()).isEqualTo(2L);
    }

    @Test
    void duplicateShipmentEventIsProcessedOnce() {
        courier(1, KYIV, "20", true, null, null);
        long shipmentId = nextShipmentId++;
        ShipmentEventMessage event = createdEvent(shipmentId, KYIV, "1", "STANDARD");

        events.process(event);
        events.process(event);

        assertThat(offers.count()).isEqualTo(1);
    }

    @Test
    void assignmentsEndpointListsActiveDeliveriesWithAddresses() throws Exception {
        courier(1, KYIV, "20", true, null, null);
        long shipmentId = created(KYIV, "3", "STANDARD");
        acceptCurrent(shipmentId, 1);

        mockMvc.perform(get("/couriers/1/assignments").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].shipmentId").value(shipmentId))
                .andExpect(jsonPath("$[0].pickupAddress").value("Khreshchatyk 1"))
                .andExpect(jsonPath("$[0].deliveryAddress").value("Svobody 10"))
                .andExpect(jsonPath("$[0].pickedUp").value(false));
    }

    // ---------------------------------------------------------------- helpers

    private void courier(long id, String zoneCode, String maxWeight, boolean online, Double lat, Double lon) {
        couriers.save(Courier.builder()
                .userId(id)
                .displayName("Courier " + id)
                .workZone("zone")
                .workZoneCode(zoneCode)
                .maxPackageWeight(new BigDecimal(maxWeight))
                .isAvailable(online)
                .lastKnownLatitude(lat)
                .lastKnownLongitude(lon)
                .locationUpdatedAt(lat == null ? null : Instant.now())
                .build());
    }

    private void offline(long id) {
        Courier courier = couriers.findById(id).orElseThrow();
        courier.setAvailable(false);
        couriers.save(courier);
    }

    /** Займає одне з двох місць кур'єра (dispatch.max-active-assignments=2) іншою доставкою. */
    private void dispatchCapacity(long courierId) {
        acceptCurrent(created(KYIV, "1", "STANDARD"), courierId);
    }

    private long created(String pickupCode, String weight, String type) {
        long shipmentId = nextShipmentId++;
        events.process(createdEvent(shipmentId, pickupCode, weight, type));
        return shipmentId;
    }

    private ShipmentEventMessage createdEvent(long shipmentId, String pickupCode, String weight, String type) {
        return new ShipmentEventMessage(UUID.randomUUID(), ShipmentEventMessage.SHIPMENT_CREATED, Instant.now(),
                snapshot(shipmentId, pickupCode, weight, type));
    }

    private static ShipmentSnapshot snapshot(long shipmentId, String pickupCode, String weight, String type) {
        return new ShipmentSnapshot(shipmentId, 42L, "WAITING_FOR_COURIER",
                "Khreshchatyk 1", pickupCode, 50.4501, 30.5234,
                "Svobody 10", LVIV, 49.8397, 24.0297,
                new BigDecimal(weight), new BigDecimal("612.4"), new BigDecimal("120.50"), type);
    }

    private ShipmentStatusChangedMessage statusEvent(long shipmentId, String status, Long courierId) {
        return new ShipmentStatusChangedMessage(UUID.randomUUID(), ShipmentStatusChangedMessage.TYPE, Instant.now(),
                shipmentId, status, null, courierId, "COURIER", null);
    }

    private Assignment acceptCurrent(long shipmentId, long courierId) {
        DeliveryOffer offer = pendingOffer(shipmentId);
        assertThat(offer.getCourier().getUserId()).isEqualTo(courierId);
        return dispatchService.acceptOffer(courierId, offer.getOfferId());
    }

    private DeliveryOffer pendingOffer(long shipmentId) {
        List<DeliveryOffer> pending = offers.findWithCourierByShipmentIdAndStatus(shipmentId, OfferStatus.PENDING);
        assertThat(pending).as("pending offers for shipment %d", shipmentId).hasSize(1);
        return pending.getFirst();
    }

    private void expireNow(DeliveryOffer offer) {
        DeliveryOffer stored = offers.findById(offer.getOfferId()).orElseThrow();
        stored.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        offers.save(stored);
    }

    private DispatchRequestStatus request(long shipmentId) {
        return requests.findById(shipmentId).orElseThrow().getStatus();
    }

    private DispatchEvent lastDispatchEvent() {
        OutboxEvent last = outbox.findAll().stream()
                .max((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .orElseThrow();
        return jsonMapper.readValue(last.getPayload(), DispatchEvent.class);
    }
}
