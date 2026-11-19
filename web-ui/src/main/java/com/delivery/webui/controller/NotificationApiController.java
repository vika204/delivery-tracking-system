package com.delivery.webui.controller;

import com.delivery.webui.client.NotificationClient;
import com.delivery.webui.controller.support.LoopbackOnly;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/notifications")
public class NotificationApiController {

    private final NotificationClient client;
    private final long userId;

    public NotificationApiController(NotificationClient client, @Value("${shipment.creation.sender-id}") long userId) {
        this.client = client;
        this.userId = userId;
    }

    @ModelAttribute
    void requireLocalAccess(HttpServletRequest request, HttpServletResponse response) {
        LoopbackOnly.require(request, "Notifications");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    @GetMapping("/unread")
    public boolean hasUnreadNotifications() {
        return client.hasUnreadNotifications(userId);
    }

    @PostMapping("/read")
    public boolean markNotificationsRead(@RequestParam long throughId) {
        if (throughId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "throughId must be positive");
        }
        return client.markNotificationsRead(userId, throughId);
    }
}
