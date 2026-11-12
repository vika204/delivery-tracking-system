package com.delivery.dispatch.repository;

import com.delivery.dispatch.entity.Assignment;
import com.delivery.dispatch.entity.AssignmentStatus;
import com.delivery.dispatch.entity.Courier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class AssignmentRepositoryTest {

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private CourierRepository courierRepository;

    @Test
    void findsNewestActiveAssignmentForRequestedShipmentOnly() {
        Courier firstCourier = courierRepository.save(courier(
                10L,
                "First Courier"
        ));
        Courier currentCourier = courierRepository.save(courier(
                11L,
                "Current Courier"
        ));
        Courier otherShipmentCourier = courierRepository.save(courier(
                12L,
                "Other Shipment Courier"
        ));

        assignmentRepository.save(assignment(
                15L,
                firstCourier,
                AssignmentStatus.ACTIVE
        ));
        assignmentRepository.save(assignment(
                15L,
                firstCourier,
                AssignmentStatus.COMPLETED
        ));
        assignmentRepository.save(assignment(
                99L,
                otherShipmentCourier,
                AssignmentStatus.ACTIVE
        ));
        Assignment newest = assignmentRepository.save(assignment(
                15L,
                currentCourier,
                AssignmentStatus.ACTIVE
        ));

        Assignment result = assignmentRepository
                .findFirstByShipmentIdAndStatusOrderByAssignmentIdDesc(
                        15L,
                        AssignmentStatus.ACTIVE
                )
                .orElseThrow();

        assertThat(result.getAssignmentId())
                .isEqualTo(newest.getAssignmentId());
        assertThat(result.getShipmentId()).isEqualTo(15L);
        assertThat(result.getCourier().getDisplayName())
                .isEqualTo("Current Courier");
    }

    private Courier courier(long userId, String displayName) {
        return Courier.builder()
                .userId(userId)
                .displayName(displayName)
                .workZone("Kyiv")
                .maxPackageWeight(new BigDecimal("20.0"))
                .isAvailable(true)
                .build();
    }

    private Assignment assignment(
            long shipmentId,
            Courier courier,
            AssignmentStatus status
    ) {
        return Assignment.builder()
                .shipmentId(shipmentId)
                .courier(courier)
                .status(status)
                .build();
    }
}
