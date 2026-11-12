package com.delivery.dispatch.service;

import com.delivery.dispatch.dto.ShipmentCourierResponse;
import com.delivery.dispatch.entity.AssignmentStatus;
import com.delivery.dispatch.repository.AssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShipmentCourierService {

    private final AssignmentRepository assignmentRepository;

    public ShipmentCourierService(AssignmentRepository assignmentRepository) {
        this.assignmentRepository = assignmentRepository;
    }

    @Transactional(readOnly = true)
    public ShipmentCourierResponse getCurrentCourier(long shipmentId) {
        return assignmentRepository
                .findFirstByShipmentIdAndStatusOrderByAssignmentIdDesc(
                        shipmentId,
                        AssignmentStatus.ACTIVE
                )
                .map(assignment -> {
                    String displayName = assignment
                            .getCourier()
                            .getDisplayName();
                    String courierName = displayName == null
                            || displayName.isBlank()
                            ? null
                            : displayName.trim();

                    return new ShipmentCourierResponse(
                            shipmentId,
                            true,
                            courierName
                    );
                })
                .orElseGet(() -> new ShipmentCourierResponse(
                        shipmentId,
                        false,
                        null
                ));
    }
}
