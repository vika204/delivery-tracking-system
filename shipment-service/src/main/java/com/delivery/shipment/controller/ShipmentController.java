package com.delivery.shipment.controller;

import com.delivery.shipment.controller.support.RequestHeaders;
import com.delivery.shipment.dto.CancelShipmentRequest;
import com.delivery.shipment.dto.CourierShipmentView;
import com.delivery.shipment.dto.CreateShipmentRequest;
import com.delivery.shipment.dto.ShipmentBatchResponse;
import com.delivery.shipment.dto.ShipmentHistoryEntryResponse;
import com.delivery.shipment.dto.ShipmentResponse;
import com.delivery.shipment.entity.Shipment;
import com.delivery.shipment.entity.ShipmentStatus;
import com.delivery.shipment.entity.StatusGroup;
import com.delivery.shipment.exception.ShipmentAccessDeniedException;
import com.delivery.shipment.service.ShipmentLifecycleService;
import com.delivery.shipment.service.ShipmentSearchCriteria;
import com.delivery.shipment.service.ShipmentService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/shipments")
public class ShipmentController {

    private static final String IDEMPOTENT_REPLAYED_HEADER = "Idempotent-Replayed";
    private static final Logger log = LoggerFactory.getLogger(ShipmentController.class);

    private final ShipmentService shipmentService;
    private final ShipmentLifecycleService lifecycleService;

    public ShipmentController(ShipmentService shipmentService, ShipmentLifecycleService lifecycleService) {
        this.shipmentService = shipmentService;
        this.lifecycleService = lifecycleService;
    }

    @PostMapping
    public ResponseEntity<ShipmentResponse> createShipment(
            @RequestHeader(name = RequestHeaders.IDEMPOTENCY_KEY) @NotBlank @Size(max = 255) String idempotencyKey,
            @RequestHeader(RequestHeaders.USER_ID) @Positive long userId,
            @Valid @RequestBody CreateShipmentRequest request) {
        log.info("Creating shipment for user {} from quote {} with idempotencyKey {}",
                userId, request.quoteId(), idempotencyKey);
        ShipmentService.CreationResult result = shipmentService.create(idempotencyKey, userId, request);
        log.info("Shipment {} ready (replayed={})", result.shipment().getShipmentId(), result.replayed());
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (result.replayed()) {
            response.header(IDEMPOTENT_REPLAYED_HEADER, "true");
        }
        return response.body(ShipmentResponse.from(result.shipment()));
    }

    @GetMapping
    public List<ShipmentResponse> getShipments(
            @RequestParam(required = false) @Positive Long senderId,
            @RequestParam(required = false) @Positive Long courierId,
            @RequestParam(required = false) ShipmentStatus status,
            @RequestParam(required = false) StatusGroup statusGroup,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return toResponses(shipmentService.search(
                new ShipmentSearchCriteria(senderId, courierId, status, statusGroup, createdFrom, createdTo), page, size));
    }

    @GetMapping("/mine")
    public List<ShipmentResponse> getMyShipments(
            @RequestHeader(RequestHeaders.USER_ID) @Positive long userId,
            @RequestParam(required = false) ShipmentStatus status,
            @RequestParam(required = false) StatusGroup statusGroup,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return toResponses(shipmentService.search(
                new ShipmentSearchCriteria(userId, null, status, statusGroup, createdFrom, createdTo), page, size));
    }

    @GetMapping("/batch")
    public ShipmentBatchResponse getShipmentsBatch(
            @RequestParam @Size(min = 1, max = 100) List<@Positive Long> ids) {
        return shipmentService.getBatch(ids);
    }

    @GetMapping("/{id}")
    public ShipmentResponse getShipment(@PathVariable Long id) {
        log.info("Shipment lookup for shipment {}", id);
        return ShipmentResponse.from(shipmentService.getById(id));
    }

    @GetMapping("/{id}/history")
    public List<ShipmentHistoryEntryResponse> getHistory(@PathVariable Long id) {
        return shipmentService.history(id).stream().map(ShipmentHistoryEntryResponse::from).toList();
    }

    @GetMapping("/{id}/courier-view")
    public CourierShipmentView getCourierView(
            @PathVariable Long id,
            @RequestHeader(RequestHeaders.USER_ID) @Positive long courierId) {
        Shipment shipment = shipmentService.getById(id);
        if (!shipment.isAssignedCourier(courierId)) {
            throw new ShipmentAccessDeniedException("Courier " + courierId + " is not assigned to shipment " + id);
        }
        return CourierShipmentView.from(shipment);
    }


    @PostMapping("/{id}/cancel")
    public ShipmentResponse cancelShipment(
            @PathVariable Long id,
            @RequestHeader(RequestHeaders.USER_ID) @Positive long userId,
            @Valid @RequestBody(required = false) CancelShipmentRequest request) {
        String reason = request == null ? null : request.reason();
        return ShipmentResponse.from(lifecycleService.cancel(id, userId, reason));
    }


    @PostMapping("/{id}/pickup")
    public CourierShipmentView confirmPickup(
            @PathVariable Long id,
            @RequestHeader(RequestHeaders.USER_ID) @Positive long courierId) {
        return CourierShipmentView.from(lifecycleService.confirmPickup(id, courierId));
    }

    @PostMapping("/{id}/deliver")
    public CourierShipmentView completeDelivery(
            @PathVariable Long id,
            @RequestHeader(RequestHeaders.USER_ID) @Positive long courierId) {
        return CourierShipmentView.from(lifecycleService.completeDelivery(id, courierId));
    }

    private static List<ShipmentResponse> toResponses(List<Shipment> shipments) {
        return shipments.stream().map(ShipmentResponse::from).toList();
    }
}
