package com.delivery.tracking.repository;

import com.delivery.tracking.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackingRepository extends JpaRepository<Location, Long> {

}
