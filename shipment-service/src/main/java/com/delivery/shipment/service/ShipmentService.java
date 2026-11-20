package com.delivery.shipment.service;

import com.delivery.shipment.chaos.ChaosSettings;
import com.delivery.shipment.dto.CreateShipmentRequest;
import com.delivery.shipment.dto.ShipmentBatchResponse;
import com.delivery.shipment.dto.ShipmentSummary;
import com.delivery.shipment.entity.ActorType;
import com.delivery.shipment.entity.IdempotencyRecord;
import com.delivery.shipment.entity.PriceQuote;
import com.delivery.shipment.entity.Shipment;
import com.delivery.shipment.entity.ShipmentStatus;
import com.delivery.shipment.entity.ShipmentStatusHistory;
import com.delivery.shipment.exception.IdempotencyKeyReuseException;
import com.delivery.shipment.exception.QuoteException;
import com.delivery.shipment.exception.ShipmentNotFoundException;
import com.delivery.shipment.metrics.ShipmentMetrics;
import com.delivery.shipment.outbox.ShipmentEventOutbox;
import com.delivery.shipment.repository.IdempotencyRecordRepository;
import com.delivery.shipment.repository.ShipmentRepository;
import com.delivery.shipment.repository.ShipmentStatusHistoryRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Створення доставки (CreateShipment) і читання. Переходи стану — у {@link ShipmentLifecycleService}. */
@Service
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ShipmentStatusHistoryRepository historyRepository;
    private final TransactionTemplate transactionTemplate;
    private final ShipmentEventOutbox eventOutbox;
    private final PricingService pricingService;
    private final ShipmentMetrics metrics;
    private final ChaosSettings chaos;
    private final Clock clock;

    public ShipmentService(
            ShipmentRepository shipmentRepository,
            IdempotencyRecordRepository idempotencyRecordRepository,
            ShipmentStatusHistoryRepository historyRepository,
            TransactionTemplate transactionTemplate,
            ShipmentEventOutbox eventOutbox,
            PricingService pricingService,
            ShipmentMetrics metrics,
            ChaosSettings chaos,
            Clock clock
    ) {
        this.shipmentRepository = shipmentRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.historyRepository = historyRepository;
        this.transactionTemplate = transactionTemplate;
        this.eventOutbox = eventOutbox;
        this.pricingService = pricingService;
        this.metrics = metrics;
        this.chaos = chaos;
        this.clock = clock;
    }

    public CreationResult create(String idempotencyKey, long userId, CreateShipmentRequest request) {
        try {
            chaos.inject();
            CreationResult result = doCreate(idempotencyKey, userId, request);
            if (!result.replayed()) {
                metrics.shipmentCreated(result.shipment().getWeight());
            }
            return result;
        } catch (IdempotencyKeyReuseException | QuoteException clientError) {
            throw clientError;
        } catch (RuntimeException e) {
            metrics.shipmentFailed();
            throw e;
        }
    }

    private CreationResult doCreate(String idempotencyKey, long userId, CreateShipmentRequest request) {
        String requestHash = hash(userId, request);
        try {
            return transactionTemplate.execute(status -> createOrReplay(idempotencyKey, requestHash, userId, request));
        } catch (DataIntegrityViolationException _) {
            // інший запит із тим самим ключем, але іншою пропозицією закомітився першим
            return transactionTemplate.execute(status -> replay(userId, idempotencyKey, requestHash));
        }
    }

    /**
     * Спершу блокує пропозицію, потім шукає Idempotency-Key. Так паралельний повтор того самого
     * запиту чекає на перший, а після його коміту бачить уже створену доставку,
     * а не «пропозицію вже використано».
     */
    private CreationResult createOrReplay(String idempotencyKey, String requestHash, long userId,
                                          CreateShipmentRequest request) {
        PriceQuote quote = pricingService.lockQuote(request.quoteId(), userId);
        if (idempotencyRecordRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey).isPresent()) {
            return replay(userId, idempotencyKey, requestHash);
        }
        pricingService.requireUsable(quote);

        Shipment shipment = shipmentRepository.save(Shipment.builder()
                .userId(userId)
                .senderName(request.senderName().trim())
                .senderPhone(request.senderPhone().trim())
                .recipientName(request.recipientName().trim())
                .recipientPhone(request.recipientPhone().trim())
                .pickupAddress(quote.getPickupAddress())
                .pickupCityCode(quote.getPickupCityCode())
                .pickupLatitude(quote.getPickupLatitude())
                .pickupLongitude(quote.getPickupLongitude())
                .deliveryAddress(quote.getDeliveryAddress())
                .deliveryCityCode(quote.getDeliveryCityCode())
                .deliveryLatitude(quote.getDeliveryLatitude())
                .deliveryLongitude(quote.getDeliveryLongitude())
                .weight(quote.getWeight())
                .length(request.length())
                .width(request.width())
                .height(request.height())
                .distance(quote.getDistanceKm())
                .price(quote.getTotalPrice())
                .deliveryType(quote.getDeliveryType())
                .quoteId(quote.getQuoteId())
                .status(ShipmentStatus.WAITING_FOR_COURIER)
                .build());

        quote.setShipmentId(shipment.getShipmentId());

        idempotencyRecordRepository.saveAndFlush(IdempotencyRecord.builder()
                .userId(userId)
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .shipmentId(shipment.getShipmentId())
                .build());

        historyRepository.save(ShipmentStatusHistory.builder()
                .shipmentId(shipment.getShipmentId())
                .toStatus(ShipmentStatus.WAITING_FOR_COURIER)
                .changedAt(Instant.now(clock))
                .actorType(ActorType.SENDER)
                .actorId(userId)
                .build());

        eventOutbox.shipmentCreated(shipment);

        return new CreationResult(shipment, false);
    }

    private CreationResult replay(long userId, String idempotencyKey, String requestHash) {
        IdempotencyRecord existing = idempotencyRecordRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .orElseThrow();
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new IdempotencyKeyReuseException(idempotencyKey);
        }
        return new CreationResult(load(existing.getShipmentId()), true);
    }

    // ---------- Читання ----------

    @Transactional(readOnly = true)
    public Shipment getById(Long id) {
        return load(id);
    }

    private Shipment load(Long id) {
        return shipmentRepository.findById(id).orElseThrow(() -> new ShipmentNotFoundException(id));
    }

    /** Список, відсортований від найновіших (US-1.5). */
    @Transactional(readOnly = true)
    public List<Shipment> search(ShipmentSearchCriteria criteria, int page, int size) {
        return shipmentRepository.findAll(
                criteria.toSpecification(),
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("shipmentId")))
        ).getContent();
    }

    @Transactional(readOnly = true)
    public List<ShipmentStatusHistory> history(Long shipmentId) {
        load(shipmentId);
        return historyRepository.findByShipmentIdOrderByChangedAtAscHistoryIdAsc(shipmentId);
    }

    @Transactional(readOnly = true)
    public ShipmentBatchResponse getBatch(Collection<Long> ids) {
        Set<Long> requestedIds = new LinkedHashSet<>(ids);
        Map<Long, Shipment> found = shipmentRepository.findAllById(requestedIds).stream()
                .collect(Collectors.toMap(Shipment::getShipmentId, Function.identity()));
        List<ShipmentSummary> shipments = requestedIds.stream()
                .filter(found::containsKey)
                .map(id -> ShipmentSummary.from(found.get(id)))
                .toList();
        List<Long> missingIds = requestedIds.stream()
                .filter(id -> !found.containsKey(id))
                .toList();
        return new ShipmentBatchResponse(shipments, missingIds);
    }

    private String hash(long userId, CreateShipmentRequest request) {
        String canonical = Stream.of(
                        userId, request.quoteId(), request.senderName(), request.senderPhone(),
                        request.recipientName(), request.recipientPhone(),
                        plain(request.length()), plain(request.width()), plain(request.height()))
                .map(String::valueOf)
                .collect(Collectors.joining("\u0000"));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    public record CreationResult(Shipment shipment, boolean replayed) {
    }
}
