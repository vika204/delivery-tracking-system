package com.delivery.dispatch.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice
public class DispatchExceptionHandler {

    @ExceptionHandler(DispatchNotFoundException.class)
    public ProblemDetail notFound(DispatchNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Not found", ex);
    }

    @ExceptionHandler(DispatchConflictException.class)
    public ProblemDetail conflict(DispatchConflictException ex) {
        return problem(HttpStatus.CONFLICT, "dispatch-conflict", "Action not available in current state", ex);
    }

    @ExceptionHandler(DispatchAccessDeniedException.class)
    public ProblemDetail accessDenied(DispatchAccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "dispatch-access-denied", "Action not allowed for this courier", ex);
    }

    @ExceptionHandler(TrackingGrpcException.class)
    public ResponseEntity<ProblemDetail> trackingUnavailable(TrackingGrpcException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case DEADLINE_EXCEEDED -> HttpStatus.GATEWAY_TIMEOUT;
            case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ALREADY_EXISTS -> HttpStatus.CONFLICT;
            case FAILED_PRECONDITION -> HttpStatus.UNPROCESSABLE_CONTENT;
            default -> HttpStatus.BAD_GATEWAY;
        };
        ProblemDetail problem = problem(status, "tracking-" + ex.getCode().name().toLowerCase().replace('_', '-'),
                "Tracking gRPC " + ex.getCode(), ex);
        problem.setProperty("grpcStatus", ex.getCode().name());

        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        if (status == HttpStatus.SERVICE_UNAVAILABLE) {
            response.header(HttpHeaders.RETRY_AFTER, "5");
        }
        return response.body(problem);
    }

    private static ProblemDetail problem(HttpStatus status, String type, String title, Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setType(URI.create("urn:delivery:problem:" + type));
        problem.setTitle(title);
        return problem;
    }
}
