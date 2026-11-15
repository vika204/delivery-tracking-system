package com.delivery.dispatch.tracking;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Перетворює фінальний gRPC-статус tracking-service на HTTP-відповідь.
 * UNAVAILABLE (усі retries вичерпано) -> 503 + Retry-After, щоб викликач повторив пізніше.
 */
@RestControllerAdvice(assignableTypes = ShipmentTrackingController.class)
public class TrackingGrpcExceptionHandler {

    static final String RETRY_AFTER_SECONDS = "5";

    @ExceptionHandler(TrackingGrpcException.class)
    public ResponseEntity<ProblemDetail> handle(TrackingGrpcException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case DEADLINE_EXCEEDED -> HttpStatus.GATEWAY_TIMEOUT;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
            case ALREADY_EXISTS -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_GATEWAY;
        };

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail(ex));
        problem.setType(URI.create("urn:delivery:problem:tracking-" + ex.getCode().name().toLowerCase().replace('_', '-')));
        problem.setTitle("Tracking gRPC " + ex.getCode());
        problem.setProperty("grpcStatus", ex.getCode().name());

        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        if (ex.isUnavailable()) {
            response.header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS);
        }
        return response.body(problem);
    }

    private static String detail(TrackingGrpcException ex) {
        return switch (ex.getCode()) {
            case UNAVAILABLE -> "Tracking-service тимчасово недоступний. Спробуйте пізніше.";
            case DEADLINE_EXCEEDED -> "Tracking-service не відповів вчасно.";
            case NOT_FOUND -> "Для посилки ще немає координат.";
            default -> ex.getMessage();
        };
    }
}
