package com.delivery.shipment.exception;

import com.delivery.problems.DataAccessProblemExceptionHandler;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;

@RestControllerAdvice
public class GlobalExceptionHandler extends DataAccessProblemExceptionHandler {

    private static final URI SHIPMENT_NOT_FOUND = URI.create("urn:delivery:problem:shipment-not-found");
    private static final URI SHIPMENT_STATUS_CONFLICT = URI.create("urn:delivery:problem:shipment-status-conflict");
    private static final URI SHIPMENT_ACCESS_DENIED = URI.create("urn:delivery:problem:shipment-access-denied");
    private static final URI ADDRESS_NOT_GEOCODABLE = URI.create("urn:delivery:problem:address-not-geocodable");

    @ExceptionHandler(ShipmentNotFoundException.class)
    public ResponseEntity<Object> handleShipmentNotFound(ShipmentNotFoundException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(SHIPMENT_NOT_FOUND);
        problem.setTitle("Shipment not found");
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.NOT_FOUND, request);
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

    @Override
    protected String concurrentUpdateDetail() {
        return "The shipment was changed concurrently, reload it and retry";
    }

    private ResponseEntity<Object> problem(Exception ex, HttpStatus status, URI type, String title, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setType(type);
        problem.setTitle(title);
        return handleExceptionInternal(ex, problem, new HttpHeaders(), status, request);
    }
}
