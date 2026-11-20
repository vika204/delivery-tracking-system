package com.delivery.dispatch.repository;

import com.delivery.dispatch.entity.DispatchRequest;
import com.delivery.dispatch.entity.DispatchRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DispatchRequestRepository extends JpaRepository<DispatchRequest, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from DispatchRequest r where r.shipmentId = :shipmentId")
    Optional<DispatchRequest> findByIdForUpdate(Long shipmentId);

    @Query("""
            select r.shipmentId from DispatchRequest r
            where r.status in :statuses
            order by r.createdAt, r.shipmentId
            """)
    List<Long> findQueue(List<DispatchRequestStatus> statuses, Pageable pageable);

    long countByStatus(DispatchRequestStatus status);
}
