package com.delivery.dispatch.repository;

import com.delivery.dispatch.entity.Assignment;
import com.delivery.dispatch.entity.AssignmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    Optional<Assignment> findFirstByShipmentIdAndStatusOrderByAssignmentIdDesc(
            Long shipmentId,
            AssignmentStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Assignment a where a.assignmentId = :assignmentId")
    Optional<Assignment> findByIdForUpdate(Long assignmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Assignment a where a.shipmentId = :shipmentId and a.status = :status order by a.assignmentId desc")
    List<Assignment> findByShipmentIdAndStatusForUpdate(Long shipmentId, AssignmentStatus status);

    @Query("select distinct a.courier.userId from Assignment a where a.shipmentId = :shipmentId and a.status = :status")
    List<Long> findCourierIdsByShipmentIdAndStatus(Long shipmentId, AssignmentStatus status);

    @Query("""
            select a from Assignment a join fetch a.courier
            where a.courier.userId = :courierId and a.shipmentId = :shipmentId and a.status = :status
            order by a.assignmentId desc
            """)
    List<Assignment> findByCourierAndShipmentAndStatus(Long courierId, Long shipmentId, AssignmentStatus status);

    List<Assignment> findByCourierUserIdOrderByAssignmentIdDesc(Long courierId);

    List<Assignment> findByCourierUserIdAndStatusOrderByAssignmentIdDesc(Long courierId, AssignmentStatus status);

    long countByCourierUserIdAndStatus(Long courierId, AssignmentStatus status);
}
