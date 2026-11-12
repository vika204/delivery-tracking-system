package com.delivery.dispatch.service;

import com.delivery.dispatch.dto.ShipmentCourierResponse;
import com.delivery.dispatch.entity.Assignment;
import com.delivery.dispatch.entity.AssignmentStatus;
import com.delivery.dispatch.entity.Courier;
import com.delivery.dispatch.repository.AssignmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipmentCourierServiceTest {

    @Mock
    private AssignmentRepository assignmentRepository;

    @Test
    void returnsRealNameForCurrentAssignment() {
        Courier courier = Courier.builder()
                .userId(10L)
                .displayName("  Andrii Kovalenko  ")
                .build();
        Assignment assignment = Assignment.builder()
                .assignmentId(7L)
                .shipmentId(15L)
                .courier(courier)
                .status(AssignmentStatus.ACTIVE)
                .build();
        when(assignmentRepository
                .findFirstByShipmentIdAndStatusOrderByAssignmentIdDesc(
                        15L,
                        AssignmentStatus.ACTIVE
                ))
                .thenReturn(Optional.of(assignment));

        ShipmentCourierResponse response = service().getCurrentCourier(15L);

        assertThat(response).isEqualTo(new ShipmentCourierResponse(
                15L,
                true,
                "Andrii Kovalenko"
        ));
    }

    @Test
    void reportsNotAssignedWhenNoActiveAssignmentExists() {
        when(assignmentRepository
                .findFirstByShipmentIdAndStatusOrderByAssignmentIdDesc(
                        99L,
                        AssignmentStatus.ACTIVE
                ))
                .thenReturn(Optional.empty());

        ShipmentCourierResponse response = service().getCurrentCourier(99L);

        assertThat(response).isEqualTo(new ShipmentCourierResponse(
                99L,
                false,
                null
        ));
    }

    @Test
    void keepsAssignedStateWhenCourierNameIsUnknown() {
        Courier courier = Courier.builder()
                .userId(10L)
                .displayName("  ")
                .build();
        Assignment assignment = Assignment.builder()
                .assignmentId(7L)
                .shipmentId(15L)
                .courier(courier)
                .status(AssignmentStatus.ACTIVE)
                .build();
        when(assignmentRepository
                .findFirstByShipmentIdAndStatusOrderByAssignmentIdDesc(
                        15L,
                        AssignmentStatus.ACTIVE
                ))
                .thenReturn(Optional.of(assignment));

        ShipmentCourierResponse response = service().getCurrentCourier(15L);

        assertThat(response.assigned()).isTrue();
        assertThat(response.courierName()).isNull();
    }

    private ShipmentCourierService service() {
        return new ShipmentCourierService(assignmentRepository);
    }
}
