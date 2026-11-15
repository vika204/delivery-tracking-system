package com.delivery.tracking.repository;

import com.delivery.tracking.entity.RouteUpload;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteUploadRepository extends JpaRepository<RouteUpload, String> {
}
