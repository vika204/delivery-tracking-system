package com.delivery.webui.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;

@Configuration
@ConditionalOnProperty(prefix = "rate-limit", name = "enabled", matchIfMissing = true)
public class RateLimitConfiguration {

    @Bean
    TokenBucketRateLimiter tokenBucketRateLimiter(RateLimitProperties properties) {
        return new TokenBucketRateLimiter(properties, Clock.systemUTC());
    }

    @Bean
    WebMvcConfigurer rateLimitWebMvcConfigurer(TokenBucketRateLimiter limiter) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new RateLimitInterceptor(limiter)).addPathPatterns("/api/**");
            }
        };
    }
}
