package com.delivery.webui.exception;

import com.delivery.problems.ProblemExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.util.Map;

@RestControllerAdvice(annotations = RestController.class)
public class ApiExceptionHandler extends ProblemExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResourceAccessException.class)
    ProblemDetail connectionError(ResourceAccessException ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                return ProblemDetail.forStatusAndDetail(HttpStatus.GATEWAY_TIMEOUT, "Service response timed out");
            }
        }
        return unavailable();
    }

    @ExceptionHandler(ShipmentNotInTransitException.class)
    ProblemDetail notInTransit(ShipmentNotInTransitException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create("urn:delivery:problem:shipment-not-in-transit"));
        return problem;
    }

    @ExceptionHandler(RestClientResponseException.class)
    ProblemDetail rejected(RestClientResponseException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null || !status.is4xxClientError()) {
            return unavailable();
        }
        ProblemDetail upstream = readProblem(ex);
        String detail = upstream != null && upstream.getDetail() != null ? upstream.getDetail() : status.getReasonPhrase();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        if (upstream != null) {
            problem.setType(upstream.getType());
            Map<String, Object> properties = upstream.getProperties();
            if (properties != null && properties.containsKey("errors")) {
                problem.setProperty("errors", properties.get("errors"));
            }
        }
        return problem;
    }

    @ExceptionHandler(RestClientException.class)
    ProblemDetail unreachable(RestClientException ex) {
        log.warn("Backend request failed: {}", ex.getClass().getSimpleName());
        return unavailable();
    }

    @Override
    protected String internalErrorDetail() {
        return "An unexpected error occurred. Please try again later.";
    }

    private static ProblemDetail readProblem(RestClientResponseException ex) {
        try {
            return ex.getResponseBodyAs(ProblemDetail.class);
        } catch (RuntimeException unreadableBody) {
            return null;
        }
    }

    private static ProblemDetail unavailable() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "The service is temporarily unavailable");
    }
}
