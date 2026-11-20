package com.delivery.webui.controller;

import com.delivery.webui.client.NotificationClient;
import com.delivery.webui.client.dto.NotificationResponse;
import com.delivery.webui.controller.support.LoopbackOnly;
import com.delivery.webui.viewmodel.NotificationViewModel;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Controller
public class NotificationsPageController {

    static final int LIMIT = 50;
    private final NotificationClient client;
    private final long userId;

    public NotificationsPageController(NotificationClient client, @Value("${shipment.creation.sender-id}") long userId) {
        this.client = client;
        this.userId = userId;
    }

    @ModelAttribute
    void requireLocalAccess(HttpServletRequest request, HttpServletResponse response) {
        LoopbackOnly.require(request, "Notifications");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    @GetMapping("/notifications")
    public String notifications(@RequestParam(defaultValue = "false") boolean fragment, Model model) {
        List<NotificationResponse> notifications = client.getMyNotifications(userId, LIMIT);
        if (notifications == null || notifications.size() > LIMIT || notifications.stream().anyMatch(item ->
                item == null || item.notificationId() <= 0 || item.shipmentId() <= 0 || item.type() == null || item.type().isBlank()
                        || item.message() == null || item.createdAt() == null)) {
            throw new RestClientException("Notification service returned invalid notifications");
        }
        model.addAttribute("notifications", notifications.stream().map(NotificationViewModel::from).toList());
        model.addAttribute("notificationReadThrough", notifications.stream()
                .mapToLong(NotificationResponse::notificationId).max().orElse(0));
        return fragment ? "pages/notifications :: notificationContent" : "pages/notifications";
    }
}
