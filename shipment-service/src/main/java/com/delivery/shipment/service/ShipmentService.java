package com.delivery.shipment.service;

import com.delivery.events.ShipmentCreatedEvent;
import com.delivery.events.ShipmentPayload;
import com.delivery.shipment.dto.CreateShipmentRequest;
import com.delivery.shipment.dto.ShipmentBatchResponse;
import com.delivery.shipment.dto.ShipmentSummary;
import com.delivery.shipment.entity.IdempotencyRecord;
import com.delivery.shipment.entity.OutboxEvent;
import com.delivery.shipment.entity.Shipment;
import com.delivery.shipment.exception.IdempotencyKeyReuseException;
import com.delivery.shipment.exception.ShipmentNotFoundException;
import com.delivery.shipment.repository.IdempotencyRecordRepository;
import com.delivery.shipment.repository.OutboxEventRepository;
import com.delivery.shipment.repository.ShipmentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final TransactionTemplate transactionTemplate;
    private final OutboxEventRepository outboxEventRepository;
    private final JsonMapper jsonMapper;

    public ShipmentService(
            ShipmentRepository shipmentRepository,
            IdempotencyRecordRepository idempotencyRecordRepository,
            OutboxEventRepository outboxEventRepository,
            JsonMapper jsonMapper,
            TransactionTemplate transactionTemplate
    ) {
        this.shipmentRepository = shipmentRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.jsonMapper = jsonMapper;
        this.transactionTemplate = transactionTemplate;
    }

    public CreationResult create(String idempotencyKey, CreateShipmentRequest request) {
        String requestHash = hash(request);
        try {
            return transactionTemplate.execute(status -> createOrReplay(idempotencyKey, requestHash, request));
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            // another request with the same key committed first
            return transactionTemplate.execute(status -> replay(idempotencyKey, requestHash));
        }
    }

    @Transactional(readOnly = true)
    public Shipment getById(Long id) {
        return shipmentRepository.findById(id).orElseThrow(() -> new ShipmentNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Shipment> getAll() {
        return shipmentRepository.findAll();
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

    private CreationResult createOrReplay(String idempotencyKey, String requestHash, CreateShipmentRequest request) {
        if (idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            return replay(idempotencyKey, requestHash);
        }
        Shipment shipment = shipmentRepository.save(toEntity(request));
        idempotencyRecordRepository.saveAndFlush(IdempotencyRecord.builder()
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .shipmentId(shipment.getShipmentId())
                .build());

        ShipmentCreatedEvent event = new ShipmentCreatedEvent(
                UUID.randomUUID(),
                ShipmentCreatedEvent.TYPE,
                Instant.now(),
                new ShipmentPayload(
                        shipment.getShipmentId(),
                        shipment.getUserId(),
                        shipment.getStatus().name()
                )
        );

        OutboxEvent outboxEvent = OutboxEvent.builder()
                .eventId(event.eventId())
                .aggregateType("Shipment")
                .aggregateId(shipment.getShipmentId().toString())
                .eventType(event.eventType())
                .payload(jsonMapper.writeValueAsString(event))
                .published(false)
                .build();
        outboxEventRepository.save(outboxEvent);

        return new CreationResult(shipment, false);
    }

    private CreationResult replay(String idempotencyKey, String requestHash) {
        IdempotencyRecord record = idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow();
        if (!record.getRequestHash().equals(requestHash)) {
            throw new IdempotencyKeyReuseException(idempotencyKey);
        }
        return new CreationResult(getById(record.getShipmentId()), true);
    }

    private Shipment toEntity(CreateShipmentRequest request) {
        return Shipment.builder()
                .userId(request.userId())
                .recipientName(request.recipientName())
                .recipientPhone(request.recipientPhone())
                .pickupAddress(request.pickupAddress())
                .deliveryAddress(request.deliveryAddress())
                .weight(request.weight())
                .length(request.length())
                .width(request.width())
                .height(request.height())
                .distance(request.distance())
                .price(request.price())
                .status(request.status())
                .build();
    }

    private String hash(CreateShipmentRequest request) {
        String canonical = Stream.of(
                        request.userId(), request.recipientName(), request.recipientPhone(),
                        request.pickupAddress(), request.deliveryAddress(),
                        plain(request.weight()), plain(request.length()), plain(request.width()),
                        plain(request.height()), plain(request.distance()), plain(request.price()),
                        request.status())
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
