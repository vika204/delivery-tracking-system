package com.delivery.notification.controller;

import com.delivery.notification.entity.Notification;
import com.delivery.notification.repository.NotificationRepository;
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
    public Notification createNotification(@RequestBody Notification notification) {
        return notificationRepository.save(notification);
    }

    @GetMapping
    public List<Notification> getNotifications() {
        return notificationRepository.findAll();
    }

    @GetMapping("/{id}")
    public Notification getNotification(@PathVariable Long id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

}
