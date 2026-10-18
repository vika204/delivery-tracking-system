package com.delivery.shipment.repository;

import com.delivery.shipment.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    long countByPublishedFalse();

    List<OutboxEvent> findTop50ByPublishedFalseOrderByCreatedAtAsc();

    @Transactional
    @Modifying
    @Query("""
            update OutboxEvent e
            set e.published = true, e.publishedAt = :at
            where e.eventId = :id and e.published = false
            """)
    int markPublished(@Param("id") UUID id, @Param("at") LocalDateTime at);
}