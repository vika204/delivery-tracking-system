package com.delivery.notification.exception;

import com.delivery.tracking.grpcclient.TrackingGrpcException;
import com.delivery.tracking.grpcclient.TrackingGrpcProblems;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class TrackingGrpcExceptionHandler {

    @ExceptionHandler(TrackingGrpcException.class)
    public ResponseEntity<ProblemDetail> handle(TrackingGrpcException ex) {
        return TrackingGrpcProblems.toResponse(ex);
    }
}
