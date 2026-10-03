package com.delivery.shipment.service;

import com.delivery.shipment.dto.CreateShipmentRequest;
import com.delivery.shipment.dto.ShipmentBatchResponse;
import com.delivery.shipment.dto.ShipmentSummary;
import com.delivery.shipment.entity.Shipment;
import com.delivery.shipment.exception.ShipmentNotFoundException;
import com.delivery.shipment.repository.ShipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;

    public ShipmentService(ShipmentRepository shipmentRepository) {
        this.shipmentRepository = shipmentRepository;
    }

    @Transactional
    public Shipment create(CreateShipmentRequest request) {
        return shipmentRepository.save(toEntity(request));
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
}
