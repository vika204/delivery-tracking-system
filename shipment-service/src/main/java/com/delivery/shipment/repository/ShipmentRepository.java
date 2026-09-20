package com.delivery.shipment.repository;

import com.delivery.shipment.entity.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

}
