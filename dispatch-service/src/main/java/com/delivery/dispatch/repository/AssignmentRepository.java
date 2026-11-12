package com.delivery.dispatch.repository;

import com.delivery.dispatch.entity.Assignment;
import com.delivery.dispatch.entity.AssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    Optional<Assignment> findFirstByShipmentIdAndStatusOrderByAssignmentIdDesc(
            Long shipmentId,
            AssignmentStatus status
    );
}
