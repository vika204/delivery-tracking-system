package com.delivery.tracking.grpc;

import com.delivery.tracking.exception.UploadIdConflictException;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.sql.SQLTransientException;

final class GrpcStatusMapper {

    private GrpcStatusMapper() {
    }

    static StatusRuntimeException toStatus(Throwable error) {
        return switch (error) {
            case StatusRuntimeException status -> status;
            case HttpClientErrorException.NotFound _ -> Status.NOT_FOUND
                    .withDescription("shipment not found").asRuntimeException();
            case HttpClientErrorException clientError -> Status.FAILED_PRECONDITION
                    .withDescription("shipment-service rejected request: " + clientError.getStatusCode()).asRuntimeException();
            case UploadIdConflictException conflict -> Status.ALREADY_EXISTS
                    .withDescription(conflict.getMessage()).asRuntimeException();
            default -> (isTransient(error)
                    ? Status.UNAVAILABLE.withDescription("tracking dependency is temporarily unavailable")
                    : Status.INTERNAL.withDescription("internal tracking error"))
                    .withCause(error).asRuntimeException();
        };
    }

    private static boolean isTransient(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof CannotCreateTransactionException
                    || current instanceof TransientDataAccessException
                    || current instanceof DataAccessResourceFailureException
                    || current instanceof SQLTransientException
                    || current instanceof CallNotPermittedException
                    || current instanceof BulkheadFullException
                    || current instanceof ResourceAccessException
                    || current instanceof HttpServerErrorException) {
                return true;
            }
        }
        return false;
    }
}
