package com.delivery.tracking.exception;

import com.delivery.problems.DataAccessProblemExceptionHandler;
import com.delivery.tracking.dto.RecordLocationRequest;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.URI;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler
extends DataAccessProblemExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ProblemDetail bodyValidationProblem(
            MethodArgumentNotValidException ex, HttpStatusCode status, List<FieldViolation> errors) {
        if (!(ex.getBindingResult().getTarget() instanceof RecordLocationRequest)) {
            return super.bodyValidationProblem(ex, status, errors);
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                status, String.valueOf(ex.getBindingResult().getAllErrors().getFirst().getDefaultMessage()));
        problem.setType(URI.create("urn:delivery:problem:location-invalid"));
        problem.setTitle("Validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(LocationRejectedException.class)
    public ProblemDetail handleLocationRejected(LocationRejectedException ex) {
        boolean jump = ex.getReason() == LocationRejectedException.Reason.SUSPICIOUS_JUMP;
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                jump ? HttpStatus.UNPROCESSABLE_CONTENT : HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create(jump
                ? "urn:delivery:problem:location-suspicious-jump"
                : "urn:delivery:problem:location-invalid"));
        problem.setTitle("Location rejected");
        return problem;
    }

    @ExceptionHandler(HttpClientErrorException.class)
    public ProblemDetail handleShipmentClientError(
            HttpClientErrorException ex
    ) {
        ProblemDetail problem;

        try {
            problem = ex.getResponseBodyAs(ProblemDetail.class);
        } catch (RuntimeException _) {
            problem = null;
        }

        if (problem == null) {
            problem = ProblemDetail.forStatusAndDetail(
                    ex.getStatusCode(),
                    "Shipment відхилив запит."
            );
        }

        problem.setStatus(ex.getStatusCode().value());

        return problem;
    }

    @ExceptionHandler({
            CallNotPermittedException.class,
            BulkheadFullException.class,
            ResourceAccessException.class,
            HttpServerErrorException.class
    })
    public ProblemDetail handleUnavailable(Exception ex) {
        log.error("Shipment service unavailable: {}", ex.getMessage(), ex);

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Отримати відповідь від Shipment зараз неможливо. Спробуйте пізніше."
                );

        problem.setType(
                URI.create("urn:delivery:problem:shipment-unavailable")
        );
        problem.setTitle("Shipment unavailable");

        return problem;
    }

    @Override
    protected String internalErrorDetail() {
        return "Сталася внутрішня помилка сервера.";
    }
}
