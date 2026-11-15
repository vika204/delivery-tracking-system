package com.delivery.shipment.repository;

import com.delivery.shipment.entity.ShipmentStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShipmentStatusHistoryRepository extends JpaRepository<ShipmentStatusHistory, Long> {

    List<ShipmentStatusHistory> findByShipmentIdOrderByChangedAtAscHistoryIdAsc(Long shipmentId);
}
