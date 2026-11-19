package com.delivery.dispatch.service;

import com.delivery.dispatch.dto.AssignmentResponse;
import com.delivery.dispatch.dto.CourierStatusResponse;
import com.delivery.dispatch.dto.OfferResponse;
import com.delivery.dispatch.dto.RegisterCourierRequest;
import com.delivery.dispatch.dto.UpdateCourierRequest;
import com.delivery.dispatch.entity.Assignment;
import com.delivery.dispatch.entity.AssignmentStatus;
import com.delivery.dispatch.entity.Courier;
import com.delivery.dispatch.entity.CourierAvailability;
import com.delivery.dispatch.entity.DeliveryOffer;
import com.delivery.dispatch.entity.DispatchRequest;
import com.delivery.dispatch.exception.DispatchConflictException;
import com.delivery.dispatch.exception.DispatchNotFoundException;
import com.delivery.dispatch.metrics.CourierMetrics;
import com.delivery.dispatch.repository.AssignmentRepository;
import com.delivery.dispatch.repository.CourierRepository;
import com.delivery.dispatch.repository.DeliveryOfferRepository;
import com.delivery.dispatch.repository.DispatchRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Кур'єри: реєстрація, профіль, режим «на лінії» та читання для застосунку кур'єра. */
@Service
public class CourierService {

    private static final Logger log = LoggerFactory.getLogger(CourierService.class);

    private final CourierRepository courierRepository;
    private final DeliveryOfferRepository offerRepository;
    private final AssignmentRepository assignmentRepository;
    private final DispatchRequestRepository requestRepository;
    private final CourierMetrics metrics;
    private final ApplicationEventPublisher applicationEvents;
    private final DispatchService dispatchService;
    private final Clock clock;

    public CourierService(CourierRepository courierRepository, DeliveryOfferRepository offerRepository,
                          AssignmentRepository assignmentRepository, DispatchRequestRepository requestRepository,
                          CourierMetrics metrics,
                          ApplicationEventPublisher applicationEvents, DispatchService dispatchService,
                          Clock clock) {
        this.courierRepository = courierRepository;
        this.offerRepository = offerRepository;
        this.assignmentRepository = assignmentRepository;
        this.requestRepository = requestRepository;
        this.metrics = metrics;
        this.applicationEvents = applicationEvents;
        this.dispatchService = dispatchService;
        this.clock = clock;
    }

    @Transactional
    public Courier register(RegisterCourierRequest request) {
        if (courierRepository.existsById(request.userId())) {
            throw new DispatchConflictException("Courier " + request.userId() + " is already registered");
        }
        Courier courier = courierRepository.save(Courier.builder()
                .userId(request.userId())
                .displayName(trim(request.displayName()))
                .phone(trim(request.phone()))
                .workZone(request.workZone().trim())
                .workZoneCode(request.workZoneCode())
                .maxPackageWeight(request.maxPackageWeight())
                .isAvailable(Boolean.TRUE.equals(request.available()))
                .build());
        metrics.courierRegistered(courier.getMaxPackageWeight());
        if (courier.isAvailable()) {
            applicationEvents.publishEvent(new CourierCapacityFreedEvent());
        }
        return courier;
    }

    @Transactional
    public Courier update(long courierId, UpdateCourierRequest request) {
        Courier courier = courierRepository.findByIdForUpdate(courierId).orElseThrow(() -> notFound(courierId));
        if (request.displayName() != null) courier.setDisplayName(request.displayName().trim());
        if (request.phone() != null) courier.setPhone(request.phone().trim());
        if (request.workZone() != null) courier.setWorkZone(request.workZone().trim());
        if (request.workZoneCode() != null) courier.setWorkZoneCode(request.workZoneCode());
        if (request.maxPackageWeight() != null) courier.setMaxPackageWeight(request.maxPackageWeight());
        // нова зона чи вага можуть зробити кур'єра придатним для доставок у черзі
        applicationEvents.publishEvent(new CourierCapacityFreedEvent());
        return courier;
    }

    /**
     * SetCourierAvailability → CourierWentOnline / CourierWentOffline (US-3.5).
     * Офлайн з активною доставкою дозволено: її можна завершити, але нових пропозицій не буде.
     * Пропозиція на розгляді в кур'єра, що пішов офлайн, одразу передається наступному кандидату.
     */
    @Transactional
    public CourierStatusResponse setAvailability(long courierId, boolean online) {
        if (online) {
            goOnline(courierId);
        } else {
            dispatchService.goOffline(courierId);
        }
        return status(get(courierId));
    }

    private void goOnline(long courierId) {
        Courier courier = courierRepository.findByIdForUpdate(courierId).orElseThrow(() -> notFound(courierId));
        if (!courier.isAvailable()) {
            courier.setAvailable(true);
            log.info("Courier went online: courierId={}", courierId);
            applicationEvents.publishEvent(new CourierCapacityFreedEvent());
        }
    }

    @Transactional(readOnly = true)
    public Courier get(long courierId) {
        return courierRepository.findById(courierId).orElseThrow(() -> notFound(courierId));
    }

    @Transactional(readOnly = true)
    public List<Courier> list() {
        return courierRepository.findAll();
    }

    @Transactional(readOnly = true)
    public CourierStatusResponse status(long courierId) {
        return status(get(courierId));
    }

    /** Поточна пропозиція кур'єра; протермінована, але ще не закрита планувальником, не показується. */
    @Transactional(readOnly = true)
    public Optional<OfferResponse> currentOffer(long courierId) {
        get(courierId);
        LocalDateTime now = LocalDateTime.now(clock);
        return offerRepository.findPendingByCourier(courierId).stream()
                .filter(offer -> offer.getExpiresAt().isAfter(now))
                .findFirst()
                .map(offer -> toResponse(offer, now));
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> assignments(long courierId, boolean activeOnly) {
        get(courierId);
        List<Assignment> assignments = activeOnly
                ? assignmentRepository.findByCourierUserIdAndStatusOrderByAssignmentIdDesc(courierId, AssignmentStatus.ACTIVE)
                : assignmentRepository.findByCourierUserIdOrderByAssignmentIdDesc(courierId);
        return assignments.stream().map(this::toResponse).toList();
    }

    private CourierStatusResponse status(Courier courier) {
        long active = assignmentRepository.countByCourierUserIdAndStatus(courier.getUserId(), AssignmentStatus.ACTIVE);
        Long pendingOfferId = offerRepository.findPendingByCourier(courier.getUserId()).stream()
                .map(DeliveryOffer::getOfferId).findFirst().orElse(null);
        return new CourierStatusResponse(
                courier.getUserId(),
                courier.isAvailable(),
                CourierAvailability.of(courier.isAvailable(), active > 0),
                active,
                pendingOfferId
        );
    }

    private OfferResponse toResponse(DeliveryOffer offer, LocalDateTime now) {
        DispatchRequest request = requestRepository.findById(offer.getShipmentId()).orElse(null);
        return new OfferResponse(
                offer.getOfferId(),
                offer.getShipmentId(),
                offer.getStatus(),
                offer.getExpiresAt(),
                Math.max(0, Duration.between(now, offer.getExpiresAt()).toSeconds()),
                request == null ? null : request.getPickupAddress(),
                request == null ? null : request.getDeliveryAddress(),
                request == null ? null : request.getParcelWeight(),
                request == null ? null : request.getDistanceKm(),
                offer.getDistanceToPickupKm(),
                request == null ? null : request.getPrice(),
                request == null ? null : request.getDeliveryType(),
                offer.getCreatedAt()
        );
    }

    /** Призначення з адресами й винагородою для застосунку кур'єра. */
    @Transactional(readOnly = true)
    public AssignmentResponse describe(Assignment assignment) {
        return toResponse(assignment);
    }

    private AssignmentResponse toResponse(Assignment assignment) {
        DispatchRequest request = requestRepository.findById(assignment.getShipmentId()).orElse(null);
        return new AssignmentResponse(
                assignment.getAssignmentId(),
                assignment.getShipmentId(),
                assignment.getStatus(),
                assignment.getPickedUpAt() != null,
                assignment.getAssignedAt(),
                assignment.getPickedUpAt(),
                assignment.getEndedAt(),
                assignment.getEndReason(),
                request == null ? null : request.getPickupAddress(),
                request == null ? null : request.getDeliveryAddress(),
                request == null ? null : request.getParcelWeight(),
                request == null ? null : request.getDistanceKm(),
                request == null ? null : request.getPrice(),
                request == null ? null : request.getDeliveryType()
        );
    }

    private static DispatchNotFoundException notFound(long courierId) {
        return new DispatchNotFoundException("Courier " + courierId + " not found");
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
