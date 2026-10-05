package com.delivery.tracking.exception;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;

@RestControllerAdvice
public class GlobalExceptionHandler
        extends ResponseEntityExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    //сюди попадають помилки 4xx від shipment-service, наприклад 404, якщо посилку не знайдено
    @ExceptionHandler(HttpClientErrorException.class)
    public ProblemDetail handleShipmentClientError(
            HttpClientErrorException ex
    ) {
        ProblemDetail problem;

        try {
            problem = ex.getResponseBodyAs(ProblemDetail.class);
        } catch (RuntimeException decodingError) {
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

    //сюди попадають технічні помилки через які зараз не можемо нормально звернутися до shipment-service
    @ExceptionHandler({
            CallNotPermittedException.class,
            BulkheadFullException.class,
            ResourceAccessException.class,
            HttpServerErrorException.class
    })
    public ProblemDetail handleUnavailable(Exception ex) {
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

    //якщо сталася якась інша неочікувана помилка
    //це останній захист тому перед цим окремо обробляємо всі відомі нам помилки
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {

        log.error("Unexpected error", ex);

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Сталася внутрішня помилка сервера."
                );

        problem.setType(
                URI.create("urn:delivery:problem:internal-error")
        );
        problem.setTitle("Internal server error");

        return problem;
    }
}