package com.delivery.webui.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitInterceptorTest {

    private final TokenBucketRateLimiter limiter = mock(TokenBucketRateLimiter.class);
    private final RateLimitInterceptor interceptor = new RateLimitInterceptor(limiter);
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/notifications/unread");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    RateLimitInterceptorTest() {
        request.setRemoteAddr("10.0.0.7");
    }

    @Test
    void letsRequestThroughAndReportsQuotaHeaders() {
        when(limiter.tryAcquire("10.0.0.7")).thenReturn(new RateLimitDecision(true, 60, 41, 0));

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("60");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("41");
    }

    @Test
    void throwsProblemWithRetryAfterWhenLimitExceeded() {
        when(limiter.tryAcquire("10.0.0.7")).thenReturn(new RateLimitDecision(false, 60, 0, 12));

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOfSatisfying(RateLimitExceededException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(ex.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("12");
                    assertThat(ex.getBody().getType()).isEqualTo(RateLimitExceededException.TYPE);
                });
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
    }
}
