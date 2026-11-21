package com.delivery.notification.service;

import com.delivery.notification.dto.NotificationRequest;
import com.delivery.notification.dto.NotificationResponse;
import com.delivery.notification.entity.Notification;
import com.delivery.notification.repository.NotificationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public NotificationResponse createNotification(NotificationRequest request) {
        Notification notification = new Notification();
        notification.setUserId(request.userId());
        notification.setShipmentId(request.shipmentId());
        notification.setType(request.type());
        notification.setMessage(request.message());
        notification.setRead(request.isRead());
        return NotificationResponse.from(notificationRepository.save(notification));
    }

    public List<NotificationResponse> getNotifications() {
        return notificationRepository.findAll().stream().map(NotificationResponse::from).toList();
    }

    public NotificationResponse getNotification(Long id) {
        return notificationRepository.findById(id)
                .map(NotificationResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public boolean hasUnreadNotifications(long userId) {
        return notificationRepository.existsByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public boolean markNotificationsRead(long userId, long throughId) {
        notificationRepository.markReadThroughForUser(userId, throughId);
        return notificationRepository.existsByUserIdAndIsReadFalse(userId);
    }

    public List<NotificationResponse> getMyNotifications(long userId, int limit) {
        return notificationRepository.findByUserIdOrderByCreatedAtDescNotificationIdDesc(userId, PageRequest.of(0, limit))
                .stream().map(NotificationResponse::from).toList();
    }
}
