package com.delivery.dispatch.repository;

import com.delivery.dispatch.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop50ByPublishedFalseOrderByCreatedAtAsc();

    @Transactional
    @Modifying
    @Query("update OutboxEvent e set e.published = true, e.publishedAt = :publishedAt where e.eventId = :eventId and e.published = false")
    int markPublished(UUID eventId, Instant publishedAt);
}
