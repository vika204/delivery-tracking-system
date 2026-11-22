package com.delivery.dispatch.service;

import com.delivery.dispatch.config.DispatchProperties;
import com.delivery.dispatch.entity.Assignment;
import com.delivery.dispatch.entity.AssignmentStatus;
import com.delivery.dispatch.entity.Courier;
import com.delivery.dispatch.entity.DeliveryOffer;
import com.delivery.dispatch.entity.DispatchRequest;
import com.delivery.dispatch.entity.DispatchRequestStatus;
import com.delivery.dispatch.entity.OfferStatus;
import com.delivery.dispatch.event.CourierCapacityFreedEvent;
import com.delivery.dispatch.event.ShipmentSnapshot;
import com.delivery.dispatch.exception.DispatchAccessDeniedException;
import com.delivery.dispatch.exception.DispatchConflictException;
import com.delivery.dispatch.exception.DispatchNotFoundException;
import com.delivery.dispatch.exception.OfferNoLongerAvailableException;
import com.delivery.dispatch.geo.ServiceZones;
import com.delivery.dispatch.metrics.DispatchMetrics;
import com.delivery.dispatch.outbox.DispatchEventOutbox;
import com.delivery.dispatch.repository.AssignmentRepository;
import com.delivery.dispatch.repository.CourierRepository;
import com.delivery.dispatch.repository.DeliveryOfferRepository;
import com.delivery.dispatch.repository.DispatchRequestRepository;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DispatchService {

    private static final String NOT_FOUND_SUFFIX = " not found";

    private static final Logger log = LoggerFactory.getLogger(DispatchService.class);

    private final DispatchRequestRepository requestRepository;
    private final CourierRepository courierRepository;
    private final DeliveryOfferRepository offerRepository;
    private final AssignmentRepository assignmentRepository;
    private final DispatchEventOutbox eventOutbox;
    private final ServiceZones zones;
    private final DispatchProperties properties;
    private final DispatchMetrics metrics;
    private final ApplicationEventPublisher applicationEvents;
    private final Clock clock;

    @Transactional
    public void onShipmentCreated(ShipmentSnapshot shipmentData) {
        Optional<DispatchRequest> existing = requestRepository.findByIdForUpdate(shipmentData.shipmentId());
        if (existing.isPresent()) {
            log.info("Dispatch request already exists: shipmentId={}, status={}",
                    shipmentData.shipmentId(), existing.get().getStatus());
            return;
        }
        Instant now = Instant.now(clock);
        DispatchRequest request = DispatchRequest.builder()
                .shipmentId(shipmentData.shipmentId())
                .senderId(shipmentData.userId())
                .pickupAddress(shipmentData.pickupAddress())
                .pickupCityCode(shipmentData.pickupCityCode())
                .pickupLatitude(shipmentData.pickupLatitude())
                .pickupLongitude(shipmentData.pickupLongitude())
                .deliveryAddress(shipmentData.deliveryAddress())
                .deliveryCityCode(shipmentData.deliveryCityCode())
                .deliveryLatitude(shipmentData.deliveryLatitude())
                .deliveryLongitude(shipmentData.deliveryLongitude())
                .parcelWeight(shipmentData.weight())
                .distanceKm(shipmentData.distance())
                .price(shipmentData.price())
                .status(DispatchRequestStatus.SEARCHING)
                .createdAt(now)
                .updatedAt(now)
                .build();
        request = requestRepository.save(request);
        log.info("Courier search started: shipmentId={}, pickupCityCode={}, weight={}",
                request.getShipmentId(), request.getPickupCityCode(), request.getParcelWeight());
        offerDelivery(request);
    }

    @Transactional
    public void onShipmentStatusChanged(long shipmentId, String status, Long courierId) {
        if ("IN_TRANSIT".equals(status)) {
            onParcelPickedUp(shipmentId, courierId);
            return;
        }
        boolean cancelled = "CANCELLED".equals(status);
        if (!cancelled && !"DELIVERED".equals(status)) {
            log.debug("Shipment status not relevant for dispatch: shipmentId={}, status={}", shipmentId, status);
            return;
        }
        List<DeliveryOffer> pending = cancelled
                ? offerRepository.findByShipmentIdAndStatusForUpdate(shipmentId, OfferStatus.PENDING)
                : List.of();
        DispatchRequest request = requestRepository.findByIdForUpdate(shipmentId).orElse(null);
        if (cancelled) {
            if (request == null) {
                Instant now = Instant.now(clock);
                requestRepository.save(DispatchRequest.builder()
                        .shipmentId(shipmentId)
                        .status(DispatchRequestStatus.CANCELLED)
                        .createdAt(now)
                        .updatedAt(now)
                        .build());
                return;
            }
            if (request.getStatus() == DispatchRequestStatus.CANCELLED) {
                return;
            }
            cancelOffers(pending, "shipment cancelled");
        }

        AssignmentStatus assignmentStatus = cancelled ? AssignmentStatus.CANCELLED : AssignmentStatus.COMPLETED;
        DispatchRequestStatus requestStatus = cancelled ? DispatchRequestStatus.CANCELLED : DispatchRequestStatus.COMPLETED;
        String endReason = cancelled ? "shipment cancelled by sender" : null;
        List<Assignment> assignments = assignmentRepository.findByShipmentIdAndStatusForUpdate(
                shipmentId, AssignmentStatus.ACTIVE);
        for (Assignment assignment : assignments) {
            assignment.setStatus(assignmentStatus);
            assignment.setEndedAt(LocalDateTime.now(clock));
            assignment.setEndReason(endReason);
        }
        if (request != null) {
            request.setStatus(requestStatus);
            request.setUpdatedAt(Instant.now(clock));
        }
        if (!assignments.isEmpty()) {
            applicationEvents.publishEvent(new CourierCapacityFreedEvent());
        }
        log.info("Shipment finished: shipmentId={}, status={}, offersCancelled={}, assignmentsClosed={}",
                shipmentId, status, pending.size(), assignments.size());
    }

    private void onParcelPickedUp(long shipmentId, Long courierId) {
        List<DeliveryOffer> pending = offerRepository.findByShipmentIdAndStatusForUpdate(shipmentId, OfferStatus.PENDING);
        DispatchRequest request = requestRepository.findByIdForUpdate(shipmentId).orElse(null);
        if (request == null || courierId == null) {
            log.warn("Cannot reconcile assignment: shipmentId={}, courierId={}, requestFound={}",
                    shipmentId, courierId, request != null);
            return;
        }

        List<Assignment> active = assignmentRepository.findByShipmentIdAndStatusForUpdate(shipmentId, AssignmentStatus.ACTIVE);
        Assignment pickedUpAssignment = active.stream()
                .filter(a -> a.getCourier().getUserId().equals(courierId))
                .findFirst()
                .orElse(null);

        if (pickedUpAssignment == null) {
            pickedUpAssignment = assignmentRepository
                    .findByCourierAndShipmentAndStatus(courierId, shipmentId, AssignmentStatus.COURIER_WITHDREW)
                    .stream().findFirst().orElse(null);
            if (pickedUpAssignment == null) {
                log.error("Shipment reports courier without dispatch assignment: shipmentId={}, courierId={}",
                        shipmentId, courierId);
                return;
            }
            pickedUpAssignment.setStatus(AssignmentStatus.ACTIVE);
            pickedUpAssignment.setEndedAt(null);
            pickedUpAssignment.setEndReason(null);
            cancelOffers(pending, "courier withdrawal superseded by shipment progress");
            log.warn("Courier withdrawal reverted: shipment progressed with courier: shipmentId={}, courierId={}",
                    shipmentId, courierId);
        }

        boolean releasedOthers = false;
        for (Assignment other : active) {
            if (other != pickedUpAssignment) {
                other.setStatus(AssignmentStatus.CANCELLED);
                other.setEndedAt(LocalDateTime.now(clock));
                other.setEndReason("superseded: shipment progressed with courier " + courierId);
                eventOutbox.courierUnassigned(other, "superseded");
                releasedOthers = true;
            }
        }

        if (pickedUpAssignment.getPickedUpAt() == null) {
            pickedUpAssignment.setPickedUpAt(Instant.now(clock));
        }
        request.setStatus(DispatchRequestStatus.IN_PROGRESS);
        request.setUpdatedAt(Instant.now(clock));
        if (releasedOthers) {
            applicationEvents.publishEvent(new CourierCapacityFreedEvent());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DispatchRequestStatus dispatch(long shipmentId) {
        return requestRepository.findByIdForUpdate(shipmentId)
                .map(this::offerDelivery)
                .orElse(null);
    }

    private DispatchRequestStatus offerDelivery(DispatchRequest request) {
        if (!request.isSearchable()) {
            return request.getStatus();
        }
        if (!request.hasRoute()) {
            log.warn("Dispatch request has no shipment details, skipped: shipmentId={}", request.getShipmentId());
            request.setStatus(DispatchRequestStatus.WAITING_FOR_COURIERS);
            request.setUpdatedAt(Instant.now(clock));
            return request.getStatus();
        }

        Set<Long> excluded = new HashSet<>(offerRepository.findCourierIdsByShipmentIdAndStatusIn(
                request.getShipmentId(), List.of(OfferStatus.REJECTED, OfferStatus.EXPIRED)));
        excluded.addAll(assignmentRepository.findCourierIdsByShipmentIdAndStatus(
                request.getShipmentId(), AssignmentStatus.COURIER_WITHDREW));
        List<Candidate> candidates = courierRepository
                .findFree(request.getParcelWeight())
                .stream()
                .filter(courier -> !excluded.contains(courier.getUserId()))
                .filter(courier -> zones.sameZone(courier.getWorkZoneCode(), request.getPickupCityCode()))
                .map(courier -> new Candidate(courier, distanceToPickup(courier, request)))
                .sorted(Comparator
                        .comparingDouble(Candidate::distanceToPickupKm)
                        .thenComparing(c -> c.courier().getLastAssignedAt(), Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(c -> c.courier().getUserId()))
                .toList();

        request.setSearchAttempts(request.getSearchAttempts() + 1);
        request.setLastSearchAt(Instant.now(clock));

        for (Candidate candidate : candidates) {
            Courier courier = courierRepository.findByIdForUpdate(candidate.courier().getUserId()).orElse(null);
            if (courier == null || !courier.isAvailable()
                    || courier.getMaxPackageWeight().compareTo(request.getParcelWeight()) < 0
                    || !offerRepository.findPendingByCourier(courier.getUserId()).isEmpty()
                    || assignmentRepository.existsByCourierUserIdAndStatus(courier.getUserId(), AssignmentStatus.ACTIVE)) {
                continue;
            }

            LocalDateTime now = LocalDateTime.now(clock);
            DeliveryOffer offer = offerRepository.save(DeliveryOffer.builder()
                    .shipmentId(request.getShipmentId())
                    .courier(courier)
                    .status(OfferStatus.PENDING)
                    .createdAt(Instant.now(clock))
                    .expiresAt(now.plus(properties.offerTtl()))
                    .distanceToPickupKm(candidate.distanceToPickupKm())
                    .build());

            request.setStatus(DispatchRequestStatus.OFFERED);
            request.setUpdatedAt(Instant.now(clock));
            metrics.offer("sent");
            log.info("Courier proposal sent: shipmentId={}, offerId={}, courierId={}, distanceToPickupKm={}, candidates={}, attempt={}",
                    request.getShipmentId(), offer.getOfferId(), courier.getUserId(),
                    candidate.distanceToPickupKm(), candidates.size(), request.getSearchAttempts());
            return request.getStatus();
        }

        request.setStatus(DispatchRequestStatus.WAITING_FOR_COURIERS);
        request.setUpdatedAt(Instant.now(clock));
        log.info("No courier available, shipment waits for assignment: shipmentId={}, excluded={}, attempt={}",
                request.getShipmentId(), excluded.size(), request.getSearchAttempts());
        Instant now = Instant.now(clock);
        if (request.getDelayNotifiedAt() == null
                && !request.getCreatedAt().isAfter(now.minus(properties.searchDelayThreshold()))) {
            request.setDelayNotifiedAt(now);
            eventOutbox.searchDelayed(request,
                    "no available courier for " + properties.searchDelayThreshold().toMinutes() + "+ min");
            log.info("Courier search delayed: shipmentId={}, waitingSince={}, attempts={}",
                    request.getShipmentId(), request.getCreatedAt(), request.getSearchAttempts());
        }
        return request.getStatus();
    }

    private double distanceToPickup(Courier courier, DispatchRequest request) {
        double lat;
        double lon;
        Instant freshAfter = Instant.now(clock).minus(properties.locationMaxAge());
        if (courier.getLastKnownLatitude() != null && courier.getLastKnownLongitude() != null
                && courier.getLocationUpdatedAt() != null && courier.getLocationUpdatedAt().isAfter(freshAfter)) {
            lat = courier.getLastKnownLatitude();
            lon = courier.getLastKnownLongitude();
        } else {
            double[] zone = zones.zoneLocation(courier.getWorkZoneCode());
            lat = zone[0];
            lon = zone[1];
        }
        return zones.distanceKm(lat, lon, request.getPickupLatitude(), request.getPickupLongitude());
    }

    @Transactional(noRollbackFor = OfferNoLongerAvailableException.class)
    public Assignment acceptOffer(long courierId, long offerId) {
        DeliveryOffer offer = lockOwnOffer(courierId, offerId);

        if (offer.getStatus() == OfferStatus.ACCEPTED) {
            return assignmentRepository.findByCourierAndShipmentAndStatus(courierId, offer.getShipmentId(), AssignmentStatus.ACTIVE)
                    .stream().findFirst()
                    .orElseThrow(() -> new DispatchConflictException("Offer " + offerId + " was accepted but the assignment has ended"));
        }
        requirePending(offer);
        if (!offer.getExpiresAt().isAfter(LocalDateTime.now(clock))) {
            throw new DispatchConflictException("Offer " + offerId + " has expired");
        }

        DispatchRequest request = requestRepository.findByIdForUpdate(offer.getShipmentId())
                .orElseThrow(() -> new DispatchNotFoundException("Dispatch request for shipment " + offer.getShipmentId() + NOT_FOUND_SUFFIX));
        if (request.getStatus() != DispatchRequestStatus.OFFERED) {
            cancelOffers(List.of(offer), "shipment no longer waits for this offer");
            throw new OfferNoLongerAvailableException("Shipment " + offer.getShipmentId() + " is no longer available");
        }

        Courier courier = courierRepository.findByIdForUpdate(courierId).orElseThrow();
        if (!courier.isAvailable()) {
            throw new DispatchConflictException("Courier " + courierId + " is offline: go online to accept offers");
        }
        if (assignmentRepository.existsByCourierUserIdAndStatus(courierId, AssignmentStatus.ACTIVE)) {
            throw new DispatchConflictException("Courier " + courierId + " already has an active delivery");
        }
        Instant now = Instant.now(clock);
        Assignment assignment = assignmentRepository.save(Assignment.builder()
                .shipmentId(offer.getShipmentId())
                .courier(courier)
                .status(AssignmentStatus.ACTIVE)
                .assignedAt(now)
                .build());

        offer.setStatus(OfferStatus.ACCEPTED);
        offer.setRespondedAt(now);
        request.setStatus(DispatchRequestStatus.ASSIGNED);
        request.setUpdatedAt(Instant.now(clock));
        courier.setLastAssignedAt(now);

        eventOutbox.courierAssigned(assignment);
        metrics.offer("accepted");
        log.info("Courier proposal accepted: shipmentId={}, offerId={}, courierId={}, assignmentId={}",
                offer.getShipmentId(), offerId, courierId, assignment.getAssignmentId());
        return assignment;
    }

    @Transactional
    public void goOffline(long courierId) {
        List<DeliveryOffer> pending = offerRepository.findPendingByCourierForUpdate(courierId);
        List<DispatchRequest> offered = pending.stream()
                .map(offer -> requestRepository.findByIdForUpdate(offer.getShipmentId()))
                .flatMap(Optional::stream)
                .toList();
        Courier courier = courierRepository.findByIdForUpdate(courierId)
                .orElseThrow(() -> new DispatchNotFoundException("Courier " + courierId + NOT_FOUND_SUFFIX));

        if (courier.isAvailable()) {
            courier.setAvailable(false);
            log.info("Courier went offline: courierId={}, offersReleased={}", courierId, pending.size());
        }
        cancelOffers(pending, "courier went offline");
        for (DispatchRequest request : offered) {
            if (request.getStatus() == DispatchRequestStatus.OFFERED) {
                request.setStatus(DispatchRequestStatus.SEARCHING);
                offerDelivery(request);
            }
        }
    }

    @Transactional
    public void declineOffer(long courierId, long offerId) {
        DeliveryOffer offer = lockOwnOffer(courierId, offerId);
        if (offer.getStatus() == OfferStatus.REJECTED) {
            return;
        }
        requirePending(offer);

        offer.setStatus(OfferStatus.REJECTED);
        offer.setRespondedAt(Instant.now(clock));
        metrics.offer("declined");
        log.info("Courier proposal rejected: shipmentId={}, offerId={}, courierId={}",
                offer.getShipmentId(), offerId, courierId);

        searchAgain(offer.getShipmentId());
        applicationEvents.publishEvent(new CourierCapacityFreedEvent());
    }

    @Transactional
    public boolean expireOffer(long offerId) {
        DeliveryOffer offer = offerRepository.findByIdForUpdate(offerId).orElse(null);
        if (offer == null || offer.getStatus() != OfferStatus.PENDING
                || offer.getExpiresAt().isAfter(LocalDateTime.now(clock))) {
            return false;
        }
        offer.setStatus(OfferStatus.EXPIRED);
        offer.setRespondedAt(Instant.now(clock));
        metrics.offer("expired");
        log.info("Courier proposal expired: shipmentId={}, offerId={}, courierId={}",
                offer.getShipmentId(), offerId, offer.getCourier().getUserId());

        searchAgain(offer.getShipmentId());
        applicationEvents.publishEvent(new CourierCapacityFreedEvent());
        return true;
    }

    private void searchAgain(long shipmentId) {
        requestRepository.findByIdForUpdate(shipmentId).ifPresent(request -> {
            if (request.getStatus() == DispatchRequestStatus.OFFERED) {
                request.setStatus(DispatchRequestStatus.SEARCHING);
                offerDelivery(request);
            }
        });
    }

    @Transactional
    public Assignment withdrawAssignment(long courierId, long assignmentId, String reason) {
        Assignment existingAssignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new DispatchNotFoundException("Assignment " + assignmentId + NOT_FOUND_SUFFIX));
        if (!existingAssignment.getCourier().getUserId().equals(courierId)) {
            throw new DispatchAccessDeniedException("Assignment " + assignmentId + " belongs to another courier");
        }

        DispatchRequest request = requestRepository.findByIdForUpdate(existingAssignment.getShipmentId()).orElse(null);
        Assignment assignment = assignmentRepository.findByIdForUpdate(assignmentId).orElseThrow();

        if (assignment.getStatus() == AssignmentStatus.COURIER_WITHDREW) {
            return assignment;
        }
        if (assignment.getStatus() != AssignmentStatus.ACTIVE) {
            throw new DispatchConflictException("Assignment " + assignmentId + " is already " + assignment.getStatus());
        }
        if (assignment.getPickedUpAt() != null
                || (request != null && request.getStatus() == DispatchRequestStatus.IN_PROGRESS)) {
            throw new DispatchConflictException("The parcel is already picked up: withdrawal is not available, "
                    + "the delivery has to be handed over to another courier");
        }

        String endReason = reason == null || reason.isBlank() ? "courier withdrew" : reason.trim();
        assignment.setStatus(AssignmentStatus.COURIER_WITHDREW);
        assignment.setEndedAt(LocalDateTime.now(clock));
        assignment.setEndReason(endReason);
        eventOutbox.courierUnassigned(assignment, endReason);
        metrics.assignmentWithdrawn();
        log.info("Courier withdrew from assignment: shipmentId={}, assignmentId={}, courierId={}, reason={}",
                assignment.getShipmentId(), assignmentId, courierId, endReason);

        if (request != null && request.getStatus() == DispatchRequestStatus.ASSIGNED) {
            request.setStatus(DispatchRequestStatus.SEARCHING);
            offerDelivery(request);
        }
        applicationEvents.publishEvent(new CourierCapacityFreedEvent());
        return assignment;
    }

    private DeliveryOffer lockOwnOffer(long courierId, long offerId) {
        DeliveryOffer offer = offerRepository.findByIdForUpdate(offerId)
                .orElseThrow(() -> new DispatchNotFoundException("Offer " + offerId + NOT_FOUND_SUFFIX));
        if (!offer.getCourier().getUserId().equals(courierId)) {
            throw new DispatchAccessDeniedException("Offer " + offerId + " belongs to another courier");
        }
        return offer;
    }

    private static void requirePending(DeliveryOffer offer) {
        if (offer.getStatus() != OfferStatus.PENDING) {
            throw new DispatchConflictException("Offer " + offer.getOfferId() + " is already " + offer.getStatus());
        }
    }

    private void cancelOffers(List<DeliveryOffer> pending, String reason) {
        for (DeliveryOffer offer : pending) {
            offer.setStatus(OfferStatus.CANCELLED);
            offer.setRespondedAt(Instant.now(clock));
            metrics.offer("cancelled");
            log.info("Courier proposal cancelled: shipmentId={}, offerId={}, courierId={}, reason={}",
                    offer.getShipmentId(), offer.getOfferId(), offer.getCourier().getUserId(), reason);
        }
        if (!pending.isEmpty()) {
            applicationEvents.publishEvent(new CourierCapacityFreedEvent());
        }
    }

    private record Candidate(Courier courier, double distanceToPickupKm) {
    }
}
