package com.delivery.webui.controller.support;

import com.delivery.webui.service.CourierDeliveryService.ShipmentNotInTransitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.Map;

@RestControllerAdvice(annotations = RestController.class)
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

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

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected(Exception ex, WebRequest request) {
        log.error("Unhandled API error", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please try again later.");
        return handleExceptionInternal(ex, problem, HttpHeaders.EMPTY, HttpStatus.INTERNAL_SERVER_ERROR, request);
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
