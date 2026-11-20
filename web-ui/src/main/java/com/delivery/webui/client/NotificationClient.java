package com.delivery.webui.client;

import com.delivery.webui.client.dto.NotificationResponse;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

@HttpExchange("/notifications")
public interface NotificationClient {

    @GetExchange("/mine")
    List<NotificationResponse> getMyNotifications(
            @RequestHeader("X-User-Id") long userId,
            @RequestParam("limit") int limit
    );

    @GetExchange("/mine/unread")
    boolean hasUnreadNotifications(@RequestHeader("X-User-Id") long userId);

    @PostExchange("/mine/read")
    boolean markNotificationsRead(
            @RequestHeader("X-User-Id") long userId,
            @RequestParam("throughId") long throughId
    );
}
