package com.delivery.notification.controller;

import com.delivery.notification.dto.NotificationRequest;
import com.delivery.notification.dto.NotificationResponse;
import com.delivery.notification.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    public NotificationResponse createNotification(@Valid @RequestBody NotificationRequest request) {
        return notificationService.createNotification(request);
    }

    @GetMapping
    public List<NotificationResponse> getNotifications() {
        return notificationService.getNotifications();
    }

    @GetMapping("/{id}")
    public NotificationResponse getNotification(@PathVariable Long id) {
        return notificationService.getNotification(id);
    }

    @GetMapping("/mine/unread")
    public boolean hasUnreadNotifications(@RequestHeader("X-User-Id") long userId) {
        if (userId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId must be positive");
        }
        return notificationService.hasUnreadNotifications(userId);
    }

    @PostMapping("/mine/read")
    public boolean markNotificationsRead(@RequestHeader("X-User-Id") long userId, @RequestParam long throughId) {
        if (userId <= 0 || throughId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId and throughId must be positive");
        }
        return notificationService.markNotificationsRead(userId, throughId);
    }

    @GetMapping("/mine")
    public List<NotificationResponse> getMyNotifications(
            @RequestHeader("X-User-Id") long userId,
            @RequestParam(defaultValue = "50") int limit) {
        if (userId <= 0 || limit < 1 || limit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A positive user ID and limit between 1 and 100 are required");
        }
        return notificationService.getMyNotifications(userId, limit);
    }

}
