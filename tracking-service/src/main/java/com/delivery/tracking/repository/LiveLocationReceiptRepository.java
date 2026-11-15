package com.delivery.tracking.repository;

import com.delivery.tracking.entity.LiveLocationReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveLocationReceiptRepository extends JpaRepository<LiveLocationReceipt, String> {
}
