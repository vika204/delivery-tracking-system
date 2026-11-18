package com.delivery.tracking.grpcclient;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.net.URI;

/**
 * Перетворює фінальний gRPC-статус tracking-service (після всіх retries) на HTTP problem+json.
 * UNAVAILABLE -> 503 + Retry-After, щоб викликач повторив пізніше.
 */
public final class TrackingGrpcProblems {

    public static final String RETRY_AFTER_SECONDS = "5";

    private TrackingGrpcProblems() {
    }

    public static ResponseEntity<ProblemDetail> toResponse(TrackingGrpcException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case DEADLINE_EXCEEDED -> HttpStatus.GATEWAY_TIMEOUT;
            case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ALREADY_EXISTS -> HttpStatus.CONFLICT;
            case FAILED_PRECONDITION -> HttpStatus.UNPROCESSABLE_CONTENT;
            default -> HttpStatus.BAD_GATEWAY;
        };

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setType(URI.create("urn:delivery:problem:tracking-" + ex.getCode().name().toLowerCase().replace('_', '-')));
        problem.setTitle("Tracking gRPC " + ex.getCode());
        problem.setProperty("grpcStatus", ex.getCode().name());

        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        if (ex.isUnavailable()) {
            response.header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS);
        }
        return response.body(problem);
    }
}
