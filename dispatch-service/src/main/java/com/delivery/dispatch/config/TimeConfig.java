package com.delivery.dispatch.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TimeConfig {

    // UTC: LocalDateTime-колонки (expires_at, ended_at) зберігаються в UTC.
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
