package com.delivery.dispatch.repository;

import com.delivery.dispatch.entity.DeliveryOffer;
import com.delivery.dispatch.entity.OfferStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeliveryOfferRepository extends JpaRepository<DeliveryOffer, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from DeliveryOffer o join fetch o.courier where o.offerId = :offerId")
    Optional<DeliveryOffer> findByIdForUpdate(Long offerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from DeliveryOffer o where o.shipmentId = :shipmentId and o.status = :status")
    List<DeliveryOffer> findByShipmentIdAndStatusForUpdate(Long shipmentId, OfferStatus status);

    @Query("select o.offerId from DeliveryOffer o where o.status = :status and o.expiresAt <= :now order by o.expiresAt")
    List<Long> findIdsByStatusAndExpiresAtBefore(OfferStatus status, LocalDateTime now);

    @Query("select distinct o.courier.userId from DeliveryOffer o where o.shipmentId = :shipmentId and o.status in :statuses")
    List<Long> findCourierIdsByShipmentIdAndStatusIn(Long shipmentId, Collection<OfferStatus> statuses);

    @Query("""
            select o from DeliveryOffer o
            where o.courier.userId = :courierId and o.status = com.delivery.dispatch.entity.OfferStatus.PENDING
            order by o.offerId desc
            """)
    List<DeliveryOffer> findPendingByCourier(Long courierId);

    @Query("select o from DeliveryOffer o join fetch o.courier where o.shipmentId = :shipmentId and o.status = :status")
    List<DeliveryOffer> findWithCourierByShipmentIdAndStatus(Long shipmentId, OfferStatus status);

    long countByStatus(OfferStatus status);
}
