package com.delivery.webui.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

public class RateLimitInterceptor implements HandlerInterceptor {

    static final String LIMIT_HEADER = "X-RateLimit-Limit";
    static final String REMAINING_HEADER = "X-RateLimit-Remaining";

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final TokenBucketRateLimiter limiter;

    public RateLimitInterceptor(TokenBucketRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        RateLimitDecision decision = limiter.tryAcquire(request.getRemoteAddr());
        response.setHeader(LIMIT_HEADER, String.valueOf(decision.limit()));
        response.setHeader(REMAINING_HEADER, String.valueOf(decision.remaining()));
        if (!decision.allowed()) {
            log.warn("Rate limit exceeded for {} on {} {}", request.getRemoteAddr(), request.getMethod(), request.getRequestURI());
            throw new RateLimitExceededException(decision.retryAfterSeconds());
        }
        return true;
    }
}
