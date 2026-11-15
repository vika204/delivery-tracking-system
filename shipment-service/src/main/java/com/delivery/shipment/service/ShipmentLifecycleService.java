package com.delivery.shipment.service;

import com.delivery.events.DispatchEvent;
import com.delivery.shipment.dto.UpdateShipmentRequest;
import com.delivery.shipment.entity.ActorType;
import com.delivery.shipment.entity.DeliveryType;
import com.delivery.shipment.entity.ProcessedEvent;
import com.delivery.shipment.entity.Shipment;
import com.delivery.shipment.entity.ShipmentStatus;
import com.delivery.shipment.entity.ShipmentStatusHistory;
import com.delivery.shipment.exception.ShipmentNotFoundException;
import com.delivery.shipment.outbox.ShipmentEventOutbox;
import com.delivery.shipment.pricing.PriceBreakdown;
import com.delivery.shipment.pricing.PricingService;
import com.delivery.shipment.pricing.RouteQuote;
import com.delivery.shipment.repository.ProcessedEventRepository;
import com.delivery.shipment.repository.ShipmentRepository;
import com.delivery.shipment.repository.ShipmentStatusHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

/**
 * Переходи життєвого циклу доставки. Кожна зміна статусу в одній транзакції:
 * оновлює агрегат, пише запис таймлайну і подію SHIPMENT_STATUS_CHANGED в outbox.
 */
@Service
public class ShipmentLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(ShipmentLifecycleService.class);

    private final ShipmentRepository shipmentRepository;
    private final ShipmentStatusHistoryRepository historyRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ShipmentEventOutbox eventOutbox;
    private final PricingService pricingService;
    private final Clock clock;

    public ShipmentLifecycleService(
            ShipmentRepository shipmentRepository,
            ShipmentStatusHistoryRepository historyRepository,
            ProcessedEventRepository processedEventRepository,
            ShipmentEventOutbox eventOutbox,
            PricingService pricingService,
            Clock clock
    ) {
        this.shipmentRepository = shipmentRepository;
        this.historyRepository = historyRepository;
        this.processedEventRepository = processedEventRepository;
        this.eventOutbox = eventOutbox;
        this.pricingService = pricingService;
        this.clock = clock;
    }

    // ---------- Відправник ----------

    /** CancelShipment -> ShipmentCancelled (сценарій 3 dz_2). */
    @Transactional
    public Shipment cancel(long shipmentId, long senderId, String reason) {
        Shipment shipment = load(shipmentId);
        ShipmentStatus previous = shipment.getStatus();
        Long courierId = shipment.getAssignedCourierId();
        String courierName = shipment.getAssignedCourierName();

        shipment.cancelBySender(senderId, reason, Instant.now(clock));

        if (previous != shipment.getStatus()) {
            recordTransition(shipment, previous, courierId, courierName, ActorType.SENDER, senderId, reason);
            log.info("Shipment cancelled by sender: shipmentId={}, previousStatus={}, courierId={}",
                    shipmentId, previous, courierId);
        }
        return shipment;
    }

    /** Редагування до призначення кур'єра з перерахунком ціни (US-1.6, US-2.4). */
    @Transactional
    public UpdateResult update(long shipmentId, long senderId, UpdateShipmentRequest request) {
        Shipment shipment = load(shipmentId);
        shipment.requireEditableBy(senderId);

        BigDecimal previousPrice = shipment.getPrice();
        PriceBreakdown breakdown = null;

        shipment.updateContacts(request.senderName(), request.senderPhone(),
                request.recipientName(), request.recipientPhone());
        shipment.updateDimensions(request.length(), request.width(), request.height());

        if (request.changesRouteOrPrice()) {
            String pickupAddress = request.pickupAddress() != null ? request.pickupAddress().trim() : shipment.getPickupAddress();
            String pickupCode = request.pickupCityCode() != null ? request.pickupCityCode() : shipment.getPickupCityCode();
            String deliveryAddress = request.deliveryAddress() != null ? request.deliveryAddress().trim() : shipment.getDeliveryAddress();
            String deliveryCode = request.deliveryCityCode() != null ? request.deliveryCityCode() : shipment.getDeliveryCityCode();
            BigDecimal weight = request.weight() != null ? request.weight() : shipment.getWeight();
            DeliveryType type = request.deliveryType() != null ? request.deliveryType()
                    : shipment.getDeliveryType() == null ? DeliveryType.STANDARD : shipment.getDeliveryType();

            RouteQuote route = pricingService.calculate(pickupCode, pickupAddress, deliveryCode, deliveryAddress, weight, type);
            breakdown = route.breakdown();

            shipment.updateRoute(
                    pickupAddress, pickupCode, route.pickup().latitude(), route.pickup().longitude(),
                    deliveryAddress, deliveryCode, route.delivery().latitude(), route.delivery().longitude(),
                    weight, type, route.distanceKm(), breakdown.total()
            );
        }

        boolean priceRecalculated = breakdown != null;
        eventOutbox.detailsUpdated(shipment, priceRecalculated);

        log.info("Shipment details updated: shipmentId={}, priceRecalculated={}, previousPrice={}, price={}",
                shipmentId, priceRecalculated, previousPrice, shipment.getPrice());
        return new UpdateResult(shipment, priceRecalculated, previousPrice, breakdown);
    }

    // ---------- Кур'єр ----------

    @Transactional
    public Shipment startTripToPickup(long shipmentId, long courierId) {
        Shipment shipment = load(shipmentId);
        ShipmentStatus previous = shipment.getStatus();
        if (shipment.startTripToPickup(courierId, Instant.now(clock))) {
            recordTransition(shipment, previous, courierId, shipment.getAssignedCourierName(), ActorType.COURIER, courierId, null);
        }
        return shipment;
    }

    @Transactional
    public Shipment confirmPickup(long shipmentId, long courierId) {
        Shipment shipment = load(shipmentId);
        ShipmentStatus previous = shipment.getStatus();
        if (shipment.confirmPickup(courierId, Instant.now(clock))) {
            recordTransition(shipment, previous, courierId, shipment.getAssignedCourierName(), ActorType.COURIER, courierId, null);
        }
        return shipment;
    }

    @Transactional
    public Shipment completeDelivery(long shipmentId, long courierId) {
        Shipment shipment = load(shipmentId);
        ShipmentStatus previous = shipment.getStatus();
        if (shipment.completeDelivery(courierId, Instant.now(clock))) {
            recordTransition(shipment, previous, courierId, shipment.getAssignedCourierName(), ActorType.COURIER, courierId, null);
        }
        return shipment;
    }

    // ---------- Події Dispatch ----------

    /**
     * Обробляє подію Dispatch рівно один раз. Подію, що суперечить поточному стану
     * (наприклад, призначення вже скасованої доставки), фіксуємо як оброблену й ігноруємо:
     * Dispatch сам звільнить кур'єра, коли отримає ShipmentCancelled.
     */
    @Transactional
    public void onDispatchEvent(DispatchEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            log.info("Dispatch event already processed: eventId={}", event.eventId());
            return;
        }
        processedEventRepository.save(new ProcessedEvent(event.eventId(), event.eventType(), Instant.now(clock)));

        Shipment shipment = shipmentRepository.findByIdForUpdate(event.shipmentId()).orElse(null);
        if (shipment == null) {
            log.warn("Dispatch event for unknown shipment ignored: eventId={}, shipmentId={}",
                    event.eventId(), event.shipmentId());
            return;
        }

        ShipmentStatus previous = shipment.getStatus();
        boolean changed = switch (event.eventType()) {
            case DispatchEvent.COURIER_ASSIGNED ->
                    shipment.assignCourier(event.courierId(), event.courierName(), Instant.now(clock));
            case DispatchEvent.COURIER_UNASSIGNED -> shipment.unassignCourier(event.courierId());
            default -> {
                log.info("Dispatch event type not handled by shipment-service: {}", event.eventType());
                yield false;
            }
        };

        if (changed) {
            recordTransition(shipment, previous, event.courierId(), event.courierName(), ActorType.SYSTEM,
                    event.courierId(), event.reason());
            log.info("Shipment updated from dispatch: shipmentId={}, event={}, {} -> {}, courierId={}",
                    shipment.getShipmentId(), event.eventType(), previous, shipment.getStatus(), event.courierId());
        } else {
            log.warn("Dispatch event ignored in current state: shipmentId={}, event={}, status={}, courierId={}, assignedCourierId={}",
                    shipment.getShipmentId(), event.eventType(), previous, event.courierId(), shipment.getAssignedCourierId());
        }
    }

    private Shipment load(long shipmentId) {
        return shipmentRepository.findById(shipmentId).orElseThrow(() -> new ShipmentNotFoundException(shipmentId));
    }

    private void recordTransition(Shipment shipment, ShipmentStatus previous, Long courierId, String courierName,
                                  ActorType actorType, Long actorId, String reason) {
        // flush перевіряє @Version до запису таймлайну й події
        shipmentRepository.saveAndFlush(shipment);
        historyRepository.save(ShipmentStatusHistory.builder()
                .shipmentId(shipment.getShipmentId())
                .fromStatus(previous)
                .toStatus(shipment.getStatus())
                .changedAt(Instant.now(clock))
                .actorType(actorType)
                .actorId(actorId)
                .reason(reason)
                .build());
        eventOutbox.statusChanged(shipment, previous, courierId, courierName, actorType, reason);
    }

    public record UpdateResult(Shipment shipment, boolean priceRecalculated, BigDecimal previousPrice,
                               PriceBreakdown breakdown) {
    }
}
