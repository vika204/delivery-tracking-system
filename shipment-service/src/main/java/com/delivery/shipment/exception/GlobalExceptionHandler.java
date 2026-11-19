package com.delivery.shipment.exception;

import com.delivery.shipment.geo.AddressNotGeocodableException;
import com.delivery.shipment.geo.GeoapifyUnavailableException;
import com.delivery.shipment.pricing.QuoteException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final URI SHIPMENT_NOT_FOUND = URI.create("urn:delivery:problem:shipment-not-found");
    private static final URI IDEMPOTENCY_KEY_REUSE = URI.create("urn:delivery:problem:idempotency-key-reuse");
    private static final URI VALIDATION_FAILED = URI.create("urn:delivery:problem:validation-failed");
    private static final URI INTERNAL_ERROR = URI.create("urn:delivery:problem:internal-error");
    private static final URI SHIPMENT_STATUS_CONFLICT = URI.create("urn:delivery:problem:shipment-status-conflict");
    private static final URI SHIPMENT_ACCESS_DENIED = URI.create("urn:delivery:problem:shipment-access-denied");
    private static final URI CONCURRENT_UPDATE = URI.create("urn:delivery:problem:concurrent-update");
    private static final URI ADDRESS_NOT_GEOCODABLE = URI.create("urn:delivery:problem:address-not-geocodable");

    @ExceptionHandler(ShipmentNotFoundException.class)
    public ResponseEntity<Object> handleShipmentNotFound(ShipmentNotFoundException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(SHIPMENT_NOT_FOUND);
        problem.setTitle("Shipment not found");
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(IdempotencyKeyReuseException.class)
    public ResponseEntity<Object> handleIdempotencyKeyReuse(IdempotencyKeyReuseException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setType(IDEMPOTENCY_KEY_REUSE);
        problem.setTitle("Idempotency-Key reused");
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.UNPROCESSABLE_ENTITY, request);
    }

    @ExceptionHandler(ShipmentStatusConflictException.class)
    public ResponseEntity<Object> handleShipmentStatusConflict(ShipmentStatusConflictException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(SHIPMENT_STATUS_CONFLICT);
        problem.setTitle("Shipment status conflict");
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(ShipmentAccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(ShipmentAccessDeniedException ex, WebRequest request) {
        return problem(ex, HttpStatus.FORBIDDEN, SHIPMENT_ACCESS_DENIED, "Action not allowed for this user", request);
    }

    // Паралельна зміна тієї самої доставки (наприклад, кур'єр і подія Dispatch одночасно).
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Object> handleConcurrentUpdate(ObjectOptimisticLockingFailureException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "The shipment was changed concurrently, reload it and retry");
        problem.setType(CONCURRENT_UPDATE);
        problem.setTitle("Concurrent update");
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.CONFLICT, request);
    }

    // Адресу неможливо геокодувати: створення блокується (US-1.1).
    @ExceptionHandler(AddressNotGeocodableException.class)
    public ResponseEntity<Object> handleAddressNotGeocodable(AddressNotGeocodableException ex, WebRequest request) {
        return problem(ex, HttpStatus.UNPROCESSABLE_ENTITY, ADDRESS_NOT_GEOCODABLE, "Address cannot be geocoded", request);
    }

    @ExceptionHandler(GeoapifyUnavailableException.class)
    public ResponseEntity<Object> handleGeoapifyUnavailable(GeoapifyUnavailableException ex, WebRequest request) {
        return problem(ex, HttpStatus.SERVICE_UNAVAILABLE, URI.create("urn:delivery:problem:routing-unavailable"),
                "Delivery distance calculation unavailable", request);
    }

    @ExceptionHandler(QuoteException.class)
    public ResponseEntity<Object> handleQuote(QuoteException ex, WebRequest request) {
        HttpStatus status = ex.getReason() == QuoteException.Reason.ALREADY_USED
                ? HttpStatus.CONFLICT : HttpStatus.UNPROCESSABLE_ENTITY;
        URI type = URI.create("urn:delivery:problem:quote-" + ex.getReason().name().toLowerCase().replace('_', '-'));
        return problem(ex, status, type, "Price quote cannot be used", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setType(INTERNAL_ERROR);
        problem.setTitle("Internal server error");
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    private ResponseEntity<Object> problem(Exception ex, HttpStatus status, URI type, String title, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setType(type);
        problem.setTitle(title);
        return handleExceptionInternal(ex, problem, new HttpHeaders(), status, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), String.valueOf(error.getDefaultMessage())))
                .toList();
        return handleExceptionInternal(ex, validationProblem(status, errors), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> violations(result).stream())
                .toList();
        return handleExceptionInternal(ex, validationProblem(status, errors), headers, status, request);
    }

    private List<FieldViolation> violations(ParameterValidationResult result) {
        if (result instanceof ParameterErrors parameterErrors) {
            return parameterErrors.getFieldErrors().stream()
                    .map(error -> new FieldViolation(error.getField(), String.valueOf(error.getDefaultMessage())))
                    .toList();
        }
        return result.getResolvableErrors().stream()
                .map(error -> new FieldViolation(
                        result.getMethodParameter().getParameterName(), String.valueOf(error.getDefaultMessage())))
                .toList();
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        HttpHeaders problemHeaders = new HttpHeaders();
        problemHeaders.putAll(headers);
        problemHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return super.handleExceptionInternal(ex, body, problemHeaders, statusCode, request);
    }

    private ProblemDetail validationProblem(HttpStatusCode status, List<FieldViolation> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Request validation failed");
        problem.setType(VALIDATION_FAILED);
        problem.setTitle("Validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    private record FieldViolation(String field, String message) {
    }
}
