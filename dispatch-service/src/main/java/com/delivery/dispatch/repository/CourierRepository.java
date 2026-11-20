package com.delivery.dispatch.repository;

import com.delivery.dispatch.entity.Courier;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CourierRepository extends JpaRepository<Courier, Long> {

    long countByIsAvailableTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Courier c where c.userId = :userId")
    Optional<Courier> findByIdForUpdate(Long userId);

    @Query("""
            select c from Courier c
            where c.isAvailable = true
              and c.maxPackageWeight >= :weight
              and not exists (
                  select o from DeliveryOffer o
                  where o.courier = c and o.status = com.delivery.dispatch.entity.OfferStatus.PENDING)
              and not exists (
                  select a from Assignment a
                  where a.courier = c and a.status = com.delivery.dispatch.entity.AssignmentStatus.ACTIVE)
            """)
    List<Courier> findFree(BigDecimal weight);
}
