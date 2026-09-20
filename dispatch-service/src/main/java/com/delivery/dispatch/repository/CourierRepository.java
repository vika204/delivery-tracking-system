package com.delivery.dispatch.repository;

import com.delivery.dispatch.entity.Courier;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourierRepository extends JpaRepository<Courier, Long> {

}
