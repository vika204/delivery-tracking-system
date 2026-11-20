package com.delivery.shipment.repository;

import com.delivery.shipment.entity.PriceQuote;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface PriceQuoteRepository extends JpaRepository<PriceQuote, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from PriceQuote q where q.quoteId = :quoteId")
    Optional<PriceQuote> findByIdForUpdate(UUID quoteId);
}
