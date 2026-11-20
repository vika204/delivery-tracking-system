package com.delivery.webui.client;

import com.delivery.webui.client.dto.UserProfile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PutExchange;

@HttpExchange("/users/profile")
public interface UserProfileClient {

    @GetExchange
    UserProfile profile(@RequestHeader("X-User-Id") long userId);

    @PutExchange(contentType = MediaType.APPLICATION_JSON_VALUE)
    UserProfile updateProfile(@RequestHeader("X-User-Id") long userId, @RequestBody UserProfile profile);
}
