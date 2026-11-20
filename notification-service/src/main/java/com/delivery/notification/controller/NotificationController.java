package com.delivery.notification.controller;

import com.delivery.notification.dto.NotificationRequest;
import com.delivery.notification.dto.NotificationResponse;
import com.delivery.notification.entity.Notification;
import com.delivery.notification.dto.NotificationResponse;
import com.delivery.notification.repository.NotificationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @PostMapping
    public NotificationResponse createNotification(@RequestBody NotificationRequest request) {
        Notification notification = new Notification();
        notification.setUserId(request.userId());
        notification.setShipmentId(request.shipmentId());
        notification.setType(request.type());
        notification.setMessage(request.message());
        notification.setRead(request.isRead());
        return NotificationResponse.from(notificationRepository.save(notification));
    }

    @GetMapping
    public List<NotificationResponse> getNotifications() {
        return notificationRepository.findAll().stream().map(NotificationResponse::from).toList();
    }

    @GetMapping("/{id}")
    public NotificationResponse getNotification(@PathVariable Long id) {
        return notificationRepository.findById(id)
                .map(NotificationResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/mine/unread")
    public boolean hasUnreadNotifications(@RequestHeader("X-User-Id") long userId) {
        if (userId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId must be positive");
        }
        return notificationRepository.existsByUserIdAndIsReadFalse(userId);
    }

    @PostMapping("/mine/read")
    public boolean markNotificationsRead(@RequestHeader("X-User-Id") long userId, @RequestParam long throughId) {
        if (userId <= 0 || throughId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId and throughId must be positive");
        }
        notificationRepository.markReadThroughForUser(userId, throughId);
        return notificationRepository.existsByUserIdAndIsReadFalse(userId);
    }

    @GetMapping("/mine")
    public List<NotificationResponse> getMyNotifications(
            @RequestHeader("X-User-Id") long userId,
            @RequestParam(defaultValue = "50") int limit) {
        if (userId <= 0 || limit < 1 || limit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A positive user ID and limit between 1 and 100 are required");
        }
        return notificationRepository.findByUserIdOrderByCreatedAtDescNotificationIdDesc(userId, PageRequest.of(0, limit))
                .stream().map(NotificationResponse::from).toList();
    }

}
