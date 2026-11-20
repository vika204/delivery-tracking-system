package com.delivery.dispatch.service;

import com.delivery.dispatch.config.DispatchProperties;
import com.delivery.dispatch.entity.Assignment;
import com.delivery.dispatch.entity.AssignmentStatus;
import com.delivery.dispatch.entity.Courier;
import com.delivery.dispatch.entity.DeliveryOffer;
import com.delivery.dispatch.entity.DispatchRequest;
import com.delivery.dispatch.entity.DispatchRequestStatus;
import com.delivery.dispatch.entity.OfferStatus;
import com.delivery.dispatch.exception.DispatchAccessDeniedException;
import com.delivery.dispatch.exception.DispatchConflictException;
import com.delivery.dispatch.exception.OfferNoLongerAvailableException;
import com.delivery.dispatch.geo.ServiceZones;
import com.delivery.dispatch.metrics.DispatchMetrics;
import com.delivery.dispatch.outbox.DispatchEventOutbox;
import com.delivery.dispatch.repository.AssignmentRepository;
import com.delivery.dispatch.repository.CourierRepository;
import com.delivery.dispatch.repository.DeliveryOfferRepository;
import com.delivery.dispatch.repository.DispatchRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DispatchServiceAcceptOfferTest {

    private static final long COURIER_ID = 7L;
    private static final long SHIPMENT_ID = 100L;
    private static final long OFFER_ID = 55L;
    private static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");

    @Mock private DispatchRequestRepository requestRepository;
    @Mock private CourierRepository courierRepository;
    @Mock private DeliveryOfferRepository offerRepository;
    @Mock private AssignmentRepository assignmentRepository;
    @Mock private DispatchEventOutbox eventOutbox;
    @Mock private ServiceZones zones;
    @Mock private DispatchMetrics metrics;
    @Mock private ApplicationEventPublisher applicationEvents;

    private DispatchService service;
    private Courier courier;
    private DeliveryOffer offer;
    private DispatchRequest request;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        DispatchProperties properties = new DispatchProperties(
                Duration.ofSeconds(60), Duration.ofMinutes(15), 20, Duration.ofMinutes(2));
        service = new DispatchService(requestRepository, courierRepository, offerRepository, assignmentRepository,
                eventOutbox, zones, properties, metrics, applicationEvents, clock);

        courier = Courier.builder().userId(COURIER_ID).displayName("Olena").isAvailable(true).build();
        offer = DeliveryOffer.builder()
                .offerId(OFFER_ID)
                .shipmentId(SHIPMENT_ID)
                .courier(courier)
                .status(OfferStatus.PENDING)
                .expiresAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).plusSeconds(30))
                .build();
        request = DispatchRequest.builder().shipmentId(SHIPMENT_ID).status(DispatchRequestStatus.OFFERED).build();

        when(offerRepository.findByIdForUpdate(OFFER_ID)).thenReturn(Optional.of(offer));
        lenient().when(requestRepository.findByIdForUpdate(SHIPMENT_ID)).thenReturn(Optional.of(request));
        lenient().when(courierRepository.findByIdForUpdate(COURIER_ID)).thenReturn(Optional.of(courier));
        lenient().when(assignmentRepository.save(any(Assignment.class))).thenAnswer(invocation -> {
            Assignment saved = invocation.getArgument(0);
            saved.setAssignmentId(1L);
            return saved;
        });
    }

    @Test
    void acceptCreatesAssignmentAndWritesCourierAssignedToOutbox() {
        Assignment assignment = service.acceptOffer(COURIER_ID, OFFER_ID);

        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.ACTIVE);
        assertThat(assignment.getCourier()).isSameAs(courier);
        assertThat(assignment.getAssignedAt()).isEqualTo(NOW);
        assertThat(offer.getStatus()).isEqualTo(OfferStatus.ACCEPTED);
        assertThat(request.getStatus()).isEqualTo(DispatchRequestStatus.ASSIGNED);
        assertThat(courier.getLastAssignedAt()).isEqualTo(NOW);
        verify(eventOutbox).courierAssigned(assignment);
    }

    @Test
    void rejectsOfferOfAnotherCourier() {
        assertThatThrownBy(() -> service.acceptOffer(COURIER_ID + 1, OFFER_ID))
                .isInstanceOf(DispatchAccessDeniedException.class);

        assertNothingAssigned();
    }

    @Test
    void rejectsExpiredOffer() {
        offer.setExpiresAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.acceptOffer(COURIER_ID, OFFER_ID))
                .isInstanceOf(DispatchConflictException.class)
                .hasMessageContaining("expired");

        assertNothingAssigned();
    }

    @Test
    void rejectsWhenCourierWentOffline() {
        courier.setAvailable(false);

        assertThatThrownBy(() -> service.acceptOffer(COURIER_ID, OFFER_ID))
                .isInstanceOf(DispatchConflictException.class)
                .hasMessageContaining("offline");

        assertNothingAssigned();
        assertThat(offer.getStatus()).isEqualTo(OfferStatus.PENDING);
    }

    @Test
    void rejectsWhenCourierAlreadyHasActiveDelivery() {
        when(assignmentRepository.existsByCourierUserIdAndStatus(COURIER_ID, AssignmentStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> service.acceptOffer(COURIER_ID, OFFER_ID))
                .isInstanceOf(DispatchConflictException.class)
                .hasMessageContaining("active delivery");

        assertNothingAssigned();
    }

    @Test
    void cancelsOfferWhenShipmentNoLongerWaitsForIt() {
        request.setStatus(DispatchRequestStatus.CANCELLED);

        assertThatThrownBy(() -> service.acceptOffer(COURIER_ID, OFFER_ID))
                .isInstanceOf(OfferNoLongerAvailableException.class);

        assertThat(offer.getStatus()).isEqualTo(OfferStatus.CANCELLED);
        assertNothingAssigned();
    }

    @Test
    void checksAvailabilityOnlyAfterLockingCourier() {
        when(courierRepository.findByIdForUpdate(COURIER_ID)).thenAnswer(invocation -> {
            courier.setAvailable(false);
            return Optional.of(courier);
        });

        assertThatThrownBy(() -> service.acceptOffer(COURIER_ID, OFFER_ID))
                .isInstanceOf(DispatchConflictException.class)
                .hasMessageContaining("offline");
    }

    private void assertNothingAssigned() {
        verify(assignmentRepository, never()).save(any());
        verifyNoInteractions(eventOutbox);
    }
}
