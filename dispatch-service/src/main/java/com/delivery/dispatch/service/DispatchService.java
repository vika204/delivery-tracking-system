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

/**
 * Dispatch Context: пошук кур'єра, пропозиції, призначення та їх завершення.
 * <p>
 * Кур'єр має або одну пропозицію на розгляді, або одне активне призначення — не більше.
 * <p>
 * Порядок блокувань однаковий у всіх операціях — пропозиція → DispatchRequest → призначення → кур'єр,
 * тож паралельні операції (Kafka-споживачі, планувальники, HTTP) не взаємоблокуються.
 * Блокування рядка кур'єра серіалізує видачу пропозицій і прийняття: перевірки «немає пропозиції»
 * та «немає активної доставки» виконуються вже під ним.
 */
@Service
public class DispatchService {

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

    public DispatchService(
            DispatchRequestRepository requestRepository,
            CourierRepository courierRepository,
            DeliveryOfferRepository offerRepository,
            AssignmentRepository assignmentRepository,
            DispatchEventOutbox eventOutbox,
            ServiceZones zones,
            DispatchProperties properties,
            DispatchMetrics metrics,
            ApplicationEventPublisher applicationEvents,
            Clock clock
    ) {
        this.requestRepository = requestRepository;
        this.courierRepository = courierRepository;
        this.offerRepository = offerRepository;
        this.assignmentRepository = assignmentRepository;
        this.eventOutbox = eventOutbox;
        this.zones = zones;
        this.properties = properties;
        this.metrics = metrics;
        this.applicationEvents = applicationEvents;
        this.clock = clock;
    }

    // =====================================================================
    // Реакція на події Shipment
    // =====================================================================

    /** ShipmentCreated → DispatchRequest (snapshot) і перша спроба знайти кур'єра. */
    @Transactional
    public void onShipmentCreated(ShipmentSnapshot snapshot) {
        Optional<DispatchRequest> existing = requestRepository.findByIdForUpdate(snapshot.shipmentId());
        if (existing.isPresent()) {
            // уже створено (ShipmentDetailsUpdated або «надгробок» скасування прийшли раніше)
            log.info("Dispatch request already exists: shipmentId={}, status={}",
                    snapshot.shipmentId(), existing.get().getStatus());
            return;
        }
        DispatchRequest draft = newRequest(snapshot.shipmentId(), DispatchRequestStatus.SEARCHING);
        applySnapshot(draft, snapshot);
        // ID призначений (shipmentId), тож save() робить merge і повертає керовану копію
        DispatchRequest request = requestRepository.save(draft);
        log.info("Courier search started: shipmentId={}, pickupCityCode={}, weight={}, type={}",
                request.getShipmentId(), request.getPickupCityCode(), request.getParcelWeight(), request.getDeliveryType());
        dispatch(request);
    }

    /**
     * ShipmentDetailsUpdated (лише до призначення): оновлює snapshot. Активна пропозиція могла бути
     * зроблена за старими даними (інша зона чи вага), тож її скасовуємо й шукаємо заново.
     */
    @Transactional
    public void onShipmentDetailsUpdated(ShipmentSnapshot snapshot) {
        List<DeliveryOffer> pending = lockPendingOffers(snapshot.shipmentId());
        Optional<DispatchRequest> found = requestRepository.findByIdForUpdate(snapshot.shipmentId());
        if (found.isEmpty()) {
            onShipmentCreated(snapshot);
            return;
        }
        DispatchRequest request = found.get();
        if (!request.isSearchable() && request.getStatus() != DispatchRequestStatus.OFFERED) {
            applySnapshot(request, snapshot);
            log.warn("Shipment details updated after assignment, snapshot refreshed only: shipmentId={}, status={}",
                    request.getShipmentId(), request.getStatus());
            return;
        }

        applySnapshot(request, snapshot);
        cancelOffers(pending, "shipment details changed");
        request.setStatus(DispatchRequestStatus.SEARCHING);
        log.info("Courier search restarted after details update: shipmentId={}", request.getShipmentId());
        dispatch(request);
    }

    /** Зміни статусу доставки, що стосуються Dispatch (Context Map: Shipment → Dispatch). */
    @Transactional
    public void onShipmentStatusChanged(long shipmentId, String status, Long courierId) {
        switch (status) {
            case "CANCELLED" -> onShipmentCancelled(shipmentId);
            case "TO_PICKUP" -> reconcileAssignment(shipmentId, courierId, false);
            case "IN_TRANSIT" -> reconcileAssignment(shipmentId, courierId, true);
            case "DELIVERED" -> onShipmentDelivered(shipmentId);
            default -> log.debug("Shipment status not relevant for dispatch: shipmentId={}, status={}", shipmentId, status);
        }
    }

    private void onShipmentCancelled(long shipmentId) {
        List<DeliveryOffer> pending = lockPendingOffers(shipmentId);
        DispatchRequest request = requestRepository.findByIdForUpdate(shipmentId).orElseGet(() -> {
            // ShipmentCancelled випередив ShipmentCreated: «надгробок», щоб пізніший Created не запустив пошук.
            DispatchRequest tombstone = newRequest(shipmentId, DispatchRequestStatus.CANCELLED);
            return requestRepository.save(tombstone);
        });
        if (request.getStatus() == DispatchRequestStatus.CANCELLED) {
            return;
        }

        int offers = cancelOffers(pending, "shipment cancelled");
        boolean released = endActiveAssignments(shipmentId, AssignmentStatus.CANCELLED, "shipment cancelled by sender");
        request.setStatus(DispatchRequestStatus.CANCELLED);
        touch(request);

        log.info("Courier search aborted for cancelled shipment: shipmentId={}, offersCancelled={}, courierReleased={}",
                shipmentId, offers, released);
        if (released) {
            capacityFreed();
        }
    }

    private void onShipmentDelivered(long shipmentId) {
        requestRepository.findByIdForUpdate(shipmentId).ifPresent(request -> {
            request.setStatus(DispatchRequestStatus.COMPLETED);
            touch(request);
        });
        if (endActiveAssignments(shipmentId, AssignmentStatus.COMPLETED, null)) {
            log.info("Assignment completed for delivered shipment: shipmentId={}", shipmentId);
            capacityFreed();
        }
    }

    /**
     * Shipment — джерело істини щодо того, хто везе посилку. Якщо кур'єр відмовився, а
     * Shipment майже одночасно зафіксував забір посилки цим кур'єром, відмова «програє»:
     * повертаємо його призначення й скасовуємо пошук заміни.
     */
    private void reconcileAssignment(long shipmentId, Long courierId, boolean pickedUp) {
        List<DeliveryOffer> pending = lockPendingOffers(shipmentId);
        DispatchRequest request = requestRepository.findByIdForUpdate(shipmentId).orElse(null);
        if (request == null || courierId == null) {
            log.warn("Cannot reconcile assignment: shipmentId={}, courierId={}, requestFound={}",
                    shipmentId, courierId, request != null);
            return;
        }

        List<Assignment> active = assignmentRepository.findByShipmentIdAndStatusForUpdate(shipmentId, AssignmentStatus.ACTIVE);
        Assignment current = active.stream()
                .filter(a -> a.getCourier().getUserId().equals(courierId))
                .findFirst()
                .orElse(null);

        if (current == null) {
            current = assignmentRepository
                    .findByCourierAndShipmentAndStatus(courierId, shipmentId, AssignmentStatus.COURIER_WITHDREW)
                    .stream().findFirst().orElse(null);
            if (current == null) {
                log.error("Shipment reports courier without dispatch assignment: shipmentId={}, courierId={}",
                        shipmentId, courierId);
                return;
            }
            current.setStatus(AssignmentStatus.ACTIVE);
            current.setEndedAt(null);
            current.setEndReason(null);
            cancelOffers(pending, "courier withdrawal superseded by shipment progress");
            log.warn("Courier withdrawal reverted: shipment progressed with courier: shipmentId={}, courierId={}",
                    shipmentId, courierId);
        }

        Assignment kept = current;
        boolean releasedOthers = false;
        for (Assignment other : active) {
            if (other != kept) {
                other.setStatus(AssignmentStatus.CANCELLED);
                other.setEndedAt(LocalDateTime.now(clock));
                other.setEndReason("superseded: shipment progressed with courier " + courierId);
                eventOutbox.courierUnassigned(other, "superseded");
                releasedOthers = true;
            }
        }

        if (pickedUp && kept.getPickedUpAt() == null) {
            kept.setPickedUpAt(Instant.now(clock));
        }
        request.setStatus(pickedUp ? DispatchRequestStatus.IN_PROGRESS : DispatchRequestStatus.ASSIGNED);
        touch(request);
        if (releasedOthers) {
            capacityFreed();
        }
    }

    // =====================================================================
    // Пошук кур'єра
    // =====================================================================

    /**
     * Повторний пошук однієї доставки (планувальник, звільнення кур'єра) у власній транзакції.
     * REQUIRES_NEW обов'язковий: виклик з AFTER_COMMIT-слухача інакше потрапив би в уже
     * завершену транзакцію, і зміни не закомітилися б.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DispatchRequestStatus dispatch(long shipmentId) {
        return requestRepository.findByIdForUpdate(shipmentId)
                .map(this::dispatch)
                .orElse(null);
    }

    /**
     * FindCourier → SendCourierProposal. Викликається з уже заблокованим DispatchRequest.
     * Ранжування: менша відстань до точки забору → довше без замовлень.
     */
    private DispatchRequestStatus dispatch(DispatchRequest request) {
        if (!request.isSearchable()) {
            return request.getStatus();
        }
        if (!request.hasRoute()) {
            log.warn("Dispatch request has no route snapshot, skipped: shipmentId={}", request.getShipmentId());
            request.setStatus(DispatchRequestStatus.WAITING_FOR_COURIERS);
            touch(request);
            return request.getStatus();
        }

        Set<Long> excluded = excludedCouriers(request.getShipmentId());
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
            if (courier == null || !stillFree(courier, request)) {
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
            touch(request);
            metrics.offer("sent");
            log.info("Courier proposal sent: shipmentId={}, offerId={}, courierId={}, distanceToPickupKm={}, candidates={}, attempt={}",
                    request.getShipmentId(), offer.getOfferId(), courier.getUserId(),
                    candidate.distanceToPickupKm(), candidates.size(), request.getSearchAttempts());
            return request.getStatus();
        }

        request.setStatus(DispatchRequestStatus.WAITING_FOR_COURIERS);
        touch(request);
        log.info("No courier available, shipment waits for assignment: shipmentId={}, excluded={}, attempt={}",
                request.getShipmentId(), excluded.size(), request.getSearchAttempts());
        notifyDelayIfWaitingTooLong(request);
        return request.getStatus();
    }

    /** US-3.1: пошук триває довше за поріг — один раз повідомляємо про затримку (подію читає notification). */
    private void notifyDelayIfWaitingTooLong(DispatchRequest request) {
        Instant threshold = Instant.now(clock).minus(properties.searchDelayThreshold());
        if (request.getDelayNotifiedAt() != null || request.getCreatedAt().isAfter(threshold)) {
            return;
        }
        request.setDelayNotifiedAt(Instant.now(clock));
        eventOutbox.searchDelayed(request, "no available courier for " + properties.searchDelayThreshold().toMinutes() + "+ min");
        log.info("Courier search delayed: shipmentId={}, waitingSince={}, attempts={}",
                request.getShipmentId(), request.getCreatedAt(), request.getSearchAttempts());
    }

    /** Кур'єри, які вже відхилили, проґавили або покинули цю доставку, повторно її не отримують. */
    private Set<Long> excludedCouriers(long shipmentId) {
        Set<Long> excluded = new HashSet<>(offerRepository.findCourierIdsByShipmentIdAndStatusIn(
                shipmentId, List.of(OfferStatus.REJECTED, OfferStatus.EXPIRED)));
        excluded.addAll(assignmentRepository.findCourierIdsByShipmentIdAndStatus(shipmentId, AssignmentStatus.COURIER_WITHDREW));
        return excluded;
    }

    /** Повторна перевірка вже під блокуванням кур'єра: стан міг змінитися після вибірки кандидатів. */
    private boolean stillFree(Courier courier, DispatchRequest request) {
        return courier.isAvailable()
                && courier.getMaxPackageWeight().compareTo(request.getParcelWeight()) >= 0
                && offerRepository.findPendingByCourier(courier.getUserId()).isEmpty()
                && !hasActiveAssignment(courier.getUserId());
    }

    private boolean hasActiveAssignment(long courierId) {
        return assignmentRepository.existsByCourierUserIdAndStatus(courierId, AssignmentStatus.ACTIVE);
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

    // =====================================================================
    // Відповідь кур'єра на пропозицію
    // =====================================================================

    /**
     * AcceptCourierProposal → CourierProposalAccepted, CourierAssigned (US-3.2).
     * Пропозиція, призначення й подія COURIER_ASSIGNED в outbox фіксуються в одній транзакції.
     * Відхиляється, якщо пропозиція чужа, прострочена, кур'єр офлайн або вже має активну доставку.
     */
    @Transactional(noRollbackFor = OfferNoLongerAvailableException.class)
    public Assignment acceptOffer(long courierId, long offerId) {
        DeliveryOffer offer = lockOwnOffer(courierId, offerId);

        if (offer.getStatus() == OfferStatus.ACCEPTED) {
            // повторне натискання «Прийняти»
            return assignmentRepository.findByCourierAndShipmentAndStatus(courierId, offer.getShipmentId(), AssignmentStatus.ACTIVE)
                    .stream().findFirst()
                    .orElseThrow(() -> new DispatchConflictException("Offer " + offerId + " was accepted but the assignment has ended"));
        }
        requirePending(offer);
        if (!offer.getExpiresAt().isAfter(LocalDateTime.now(clock))) {
            // протерміновану пропозицію закриє планувальник і передасть доставку наступному кандидату
            throw new DispatchConflictException("Offer " + offerId + " has expired");
        }

        DispatchRequest request = requestRepository.findByIdForUpdate(offer.getShipmentId())
                .orElseThrow(() -> new DispatchNotFoundException("Dispatch request for shipment " + offer.getShipmentId() + " not found"));
        if (request.getStatus() != DispatchRequestStatus.OFFERED) {
            // зміна зберігається: виняток не відкочує транзакцію (noRollbackFor)
            cancelOffers(List.of(offer), "shipment no longer waits for this offer");
            throw new OfferNoLongerAvailableException("Shipment " + offer.getShipmentId() + " is no longer available");
        }

        Courier courier = courierRepository.findByIdForUpdate(courierId).orElseThrow();
        requireCanTakeDelivery(courier);
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
        touch(request);
        courier.setLastAssignedAt(now);

        eventOutbox.courierAssigned(assignment);
        metrics.offer("accepted");
        log.info("Courier proposal accepted: shipmentId={}, offerId={}, courierId={}, assignmentId={}",
                offer.getShipmentId(), offerId, courierId, assignment.getAssignmentId());
        return assignment;
    }

    /**
     * CourierWentOffline (US-3.5): пропозицію на розгляді одразу передаємо наступному кандидату,
     * щоб посилка не чекала тайм-ауту. Активну доставку кур'єр завершує й офлайн.
     * Порядок блокувань загальний: пропозиції → DispatchRequest → кур'єр.
     * <p>
     * Пропозиція, створена між вибіркою й блокуванням кур'єра, лишиться до тайм-ауту,
     * але прийняти її офлайн не можна (див. {@link #acceptOffer}).
     */
    @Transactional
    public void goOffline(long courierId) {
        List<DeliveryOffer> pending = offerRepository.findPendingByCourierForUpdate(courierId);
        List<DispatchRequest> offered = pending.stream()
                .map(offer -> requestRepository.findByIdForUpdate(offer.getShipmentId()))
                .flatMap(Optional::stream)
                .toList();
        Courier courier = courierRepository.findByIdForUpdate(courierId)
                .orElseThrow(() -> new DispatchNotFoundException("Courier " + courierId + " not found"));

        if (courier.isAvailable()) {
            courier.setAvailable(false);
            log.info("Courier went offline: courierId={}, offersReleased={}", courierId, pending.size());
        }
        cancelOffers(pending, "courier went offline");
        for (DispatchRequest request : offered) {
            if (request.getStatus() == DispatchRequestStatus.OFFERED) {
                request.setStatus(DispatchRequestStatus.SEARCHING);
                dispatch(request);
            }
        }
    }

    /** RejectCourierProposal → CourierProposalRejected, пропозиція наступному кандидату (US-3.2). */
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
        capacityFreed();
    }

    /** Тайм-аут відповіді → CourierProposalExpired, пропозиція наступному кандидату. */
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
        capacityFreed();
        return true;
    }

    private void searchAgain(long shipmentId) {
        requestRepository.findByIdForUpdate(shipmentId).ifPresent(request -> {
            if (request.getStatus() == DispatchRequestStatus.OFFERED) {
                request.setStatus(DispatchRequestStatus.SEARCHING);
                dispatch(request);
            }
        });
    }

    // =====================================================================
    // Відмова кур'єра від призначення
    // =====================================================================

    /**
     * DeclineAssignment → CourierAssignmentDeclined, CourierUnassigned.
     * Дозволено лише до забору посилки; кур'єр виключається з повторного пошуку (US-1.7, US-3.4).
     */
    @Transactional
    public Assignment withdrawAssignment(long courierId, long assignmentId, String reason) {
        Assignment snapshot = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new DispatchNotFoundException("Assignment " + assignmentId + " not found"));
        if (!snapshot.getCourier().getUserId().equals(courierId)) {
            throw new DispatchAccessDeniedException("Assignment " + assignmentId + " belongs to another courier");
        }

        DispatchRequest request = requestRepository.findByIdForUpdate(snapshot.getShipmentId()).orElse(null);
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
            dispatch(request);
        }
        capacityFreed();
        return assignment;
    }

    // =====================================================================
    // Допоміжне
    // =====================================================================

    private DeliveryOffer lockOwnOffer(long courierId, long offerId) {
        DeliveryOffer offer = offerRepository.findByIdForUpdate(offerId)
                .orElseThrow(() -> new DispatchNotFoundException("Offer " + offerId + " not found"));
        if (!offer.getCourier().getUserId().equals(courierId)) {
            throw new DispatchAccessDeniedException("Offer " + offerId + " belongs to another courier");
        }
        return offer;
    }

    /** Викликається під блокуванням кур'єра, тож паралельний Accept побачить уже створене призначення. */
    private void requireCanTakeDelivery(Courier courier) {
        if (!courier.isAvailable()) {
            throw new DispatchConflictException("Courier " + courier.getUserId() + " is offline: go online to accept offers");
        }
        if (hasActiveAssignment(courier.getUserId())) {
            throw new DispatchConflictException("Courier " + courier.getUserId() + " already has an active delivery");
        }
    }

    private static void requirePending(DeliveryOffer offer) {
        if (offer.getStatus() != OfferStatus.PENDING) {
            throw new DispatchConflictException("Offer " + offer.getOfferId() + " is already " + offer.getStatus());
        }
    }

    /** Пропозиції блокуються раніше за DispatchRequest — той самий порядок, що й у acceptOffer. */
    private List<DeliveryOffer> lockPendingOffers(long shipmentId) {
        return offerRepository.findByShipmentIdAndStatusForUpdate(shipmentId, OfferStatus.PENDING);
    }

    private int cancelOffers(List<DeliveryOffer> pending, String reason) {
        for (DeliveryOffer offer : pending) {
            offer.setStatus(OfferStatus.CANCELLED);
            offer.setRespondedAt(Instant.now(clock));
            metrics.offer("cancelled");
            log.info("Courier proposal cancelled: shipmentId={}, offerId={}, courierId={}, reason={}",
                    offer.getShipmentId(), offer.getOfferId(), offer.getCourier().getUserId(), reason);
        }
        if (!pending.isEmpty()) {
            capacityFreed();
        }
        return pending.size();
    }

    /** ReleaseCourier → CourierReleased. */
    private boolean endActiveAssignments(long shipmentId, AssignmentStatus endStatus, String reason) {
        List<Assignment> active = assignmentRepository.findByShipmentIdAndStatusForUpdate(shipmentId, AssignmentStatus.ACTIVE);
        for (Assignment assignment : active) {
            assignment.setStatus(endStatus);
            assignment.setEndedAt(LocalDateTime.now(clock));
            assignment.setEndReason(reason);
            log.info("Courier released: shipmentId={}, assignmentId={}, courierId={}, status={}",
                    shipmentId, assignment.getAssignmentId(), assignment.getCourier().getUserId(), endStatus);
        }
        return !active.isEmpty();
    }

    /** Після коміту кур'єр може взяти ще одну доставку — пробуємо роздати чергу очікування. */
    private void capacityFreed() {
        applicationEvents.publishEvent(new CourierCapacityFreedEvent());
    }

    private DispatchRequest newRequest(long shipmentId, DispatchRequestStatus status) {
        Instant now = Instant.now(clock);
        return DispatchRequest.builder()
                .shipmentId(shipmentId)
                .status(status)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private void applySnapshot(DispatchRequest request, ShipmentSnapshot snapshot) {
        request.setSenderId(snapshot.userId());
        request.setPickupAddress(snapshot.pickupAddress());
        request.setPickupCityCode(snapshot.pickupCityCode());
        request.setPickupLatitude(snapshot.pickupLatitude());
        request.setPickupLongitude(snapshot.pickupLongitude());
        request.setDeliveryAddress(snapshot.deliveryAddress());
        request.setDeliveryCityCode(snapshot.deliveryCityCode());
        request.setDeliveryLatitude(snapshot.deliveryLatitude());
        request.setDeliveryLongitude(snapshot.deliveryLongitude());
        request.setParcelWeight(snapshot.weight());
        request.setDistanceKm(snapshot.distance());
        request.setPrice(snapshot.price());
        request.setDeliveryType(snapshot.deliveryType());
        touch(request);
    }

    private void touch(DispatchRequest request) {
        request.setUpdatedAt(Instant.now(clock));
    }

    private record Candidate(Courier courier, double distanceToPickupKm) {
    }
}
