package com.delivery.webui.client;

import com.delivery.webui.client.dto.LoginRequest;
import com.delivery.webui.client.dto.UserRequest;
import com.delivery.webui.client.dto.UserResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/users")
public interface AuthUserClient {

    @PostExchange(contentType = MediaType.APPLICATION_JSON_VALUE)
    UserResponse createUser(@RequestBody UserRequest request);

    @PostExchange(value = "/login", contentType = MediaType.APPLICATION_JSON_VALUE)
    UserResponse login(@RequestBody LoginRequest request);
}
