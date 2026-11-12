package com.delivery.tracking.repository;

import com.delivery.tracking.entity.Location;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrackingRepository extends JpaRepository<Location, Long> {

    List<Location> findByShipmentIdAndLocationIdGreaterThanOrderByLocationIdAsc(
            Long shipmentId,
            Long afterLocationId,
            Pageable pageable
    );
}