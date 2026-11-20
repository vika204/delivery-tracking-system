package com.delivery.notification.repository;

import com.delivery.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDescNotificationIdDesc(Long userId, Pageable pageable);

    boolean existsByUserIdAndIsReadFalse(Long userId);

    @Modifying
    @Transactional
    @Query("update Notification n set n.isRead = true where n.userId = :userId "
            + "and n.notificationId <= :throughId and n.isRead = false")
    int markReadThroughForUser(@Param("userId") long userId, @Param("throughId") long throughId);
}
