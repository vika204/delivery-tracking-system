package com.delivery.webui.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;

@Component
@ConditionalOnProperty(
        prefix = "tracking.stream",
        name = "enabled",
        havingValue = "true"
)
public class TrackingSseEmitterFactory {

    public SseEmitter create(Duration timeout) {
        return new SseEmitter(timeout.toMillis());
    }
}
