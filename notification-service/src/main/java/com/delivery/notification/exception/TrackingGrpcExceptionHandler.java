package com.delivery.notification.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice
public class TrackingGrpcExceptionHandler {

    @ExceptionHandler(TrackingGrpcException.class)
    public ResponseEntity<ProblemDetail> handle(TrackingGrpcException ex) {
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
        if (status == HttpStatus.SERVICE_UNAVAILABLE) {
            response.header(HttpHeaders.RETRY_AFTER, "5");
        }
        return response.body(problem);
    }
}
