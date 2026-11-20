package com.delivery.webui.ratelimit;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

import java.net.URI;

/**
 * Rendered as an RFC 9457 problem with status 429 and a {@code Retry-After} header
 * by the standard Spring MVC error handling.
 */
public class RateLimitExceededException extends ErrorResponseException {

    static final URI TYPE = URI.create("urn:delivery:problem:rate-limit-exceeded");

    public RateLimitExceededException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, problem(retryAfterSeconds), null);
        getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
    }

    private static ProblemDetail problem(long retryAfterSeconds) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Too many requests. Retry in " + retryAfterSeconds + " s.");
        problem.setType(TYPE);
        problem.setTitle("Rate limit exceeded");
        return problem;
    }
}
