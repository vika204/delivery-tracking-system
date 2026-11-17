package com.delivery.dispatch.controller;

import com.delivery.dispatch.dto.AssignmentResponse;
import com.delivery.dispatch.dto.AvailabilityRequest;
import com.delivery.dispatch.dto.CourierResponse;
import com.delivery.dispatch.dto.CourierStatusResponse;
import com.delivery.dispatch.dto.OfferResponse;
import com.delivery.dispatch.dto.RegisterCourierRequest;
import com.delivery.dispatch.dto.UpdateCourierRequest;
import com.delivery.dispatch.dto.WithdrawAssignmentRequest;
import com.delivery.dispatch.entity.Assignment;
import com.delivery.dispatch.exception.DispatchAccessDeniedException;
import com.delivery.dispatch.service.CourierService;
import com.delivery.dispatch.service.DispatchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/couriers")
public class CourierController {

    static final String USER_ID = "X-User-Id";

    private final CourierService courierService;
    private final DispatchService dispatchService;

    public CourierController(CourierService courierService, DispatchService dispatchService) {
        this.courierService = courierService;
        this.dispatchService = dispatchService;
    }

    @PostMapping
    public ResponseEntity<CourierResponse> createCourier(@Valid @RequestBody RegisterCourierRequest request) {
        return ResponseEntity.status(201).body(CourierResponse.from(courierService.register(request)));
    }

    @GetMapping
    public List<CourierResponse> getCouriers() {
        return courierService.list().stream().map(CourierResponse::from).toList();
    }

    @GetMapping("/{id}")
    public CourierResponse getCourier(@PathVariable Long id) {
        return CourierResponse.from(courierService.get(id));
    }

    @PatchMapping("/{id}")
    public CourierResponse updateCourier(@PathVariable long id, @RequestHeader(USER_ID) @Positive long userId,
                                         @Valid @RequestBody UpdateCourierRequest request) {
        requireSelf(id, userId);
        return CourierResponse.from(courierService.update(id, request));
    }

    @GetMapping("/{id}/status")
    public CourierStatusResponse getStatus(@PathVariable long id, @RequestHeader(USER_ID) @Positive long userId) {
        requireSelf(id, userId);
        return courierService.status(id);
    }

    @PutMapping("/{id}/availability")
    public CourierStatusResponse setAvailability(@PathVariable long id, @RequestHeader(USER_ID) @Positive long userId,
                                                 @Valid @RequestBody AvailabilityRequest request) {
        requireSelf(id, userId);
        return courierService.setAvailability(id, request.online());
    }

    @GetMapping("/{id}/offers/current")
    public ResponseEntity<OfferResponse> getCurrentOffer(@PathVariable long id,
                                                         @RequestHeader(USER_ID) @Positive long userId) {
        requireSelf(id, userId);
        return courierService.currentOffer(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{id}/offers/{offerId}/accept")
    public AssignmentResponse acceptOffer(@PathVariable long id, @PathVariable long offerId,
                                          @RequestHeader(USER_ID) @Positive long userId) {
        requireSelf(id, userId);
        Assignment assignment = dispatchService.acceptOffer(id, offerId);
        return find(id, assignment.getAssignmentId());
    }

    @PostMapping("/{id}/offers/{offerId}/decline")
    public ResponseEntity<Void> declineOffer(@PathVariable long id, @PathVariable long offerId,
                                             @RequestHeader(USER_ID) @Positive long userId) {
        requireSelf(id, userId);
        dispatchService.declineOffer(id, offerId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/assignments")
    public List<AssignmentResponse> getAssignments(@PathVariable long id, @RequestHeader(USER_ID) @Positive long userId,
                                                   @RequestParam(defaultValue = "true") boolean active) {
        requireSelf(id, userId);
        return courierService.assignments(id, active);
    }

    @PostMapping("/{id}/assignments/{assignmentId}/withdraw")
    public AssignmentResponse withdrawAssignment(@PathVariable long id, @PathVariable long assignmentId,
                                                 @RequestHeader(USER_ID) @Positive long userId,
                                                 @Valid @RequestBody(required = false) WithdrawAssignmentRequest request) {
        requireSelf(id, userId);
        dispatchService.withdrawAssignment(id, assignmentId, request == null ? null : request.reason());
        return find(id, assignmentId);
    }

    private AssignmentResponse find(long courierId, long assignmentId) {
        return courierService.assignments(courierId, false).stream()
                .filter(a -> a.assignmentId() == assignmentId)
                .findFirst()
                .orElseThrow();
    }

    private static void requireSelf(long courierId, long userId) {
        if (courierId != userId) {
            throw new DispatchAccessDeniedException("User " + userId + " cannot act as courier " + courierId);
        }
    }
}
