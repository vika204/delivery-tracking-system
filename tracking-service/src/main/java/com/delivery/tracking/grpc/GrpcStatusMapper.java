package com.delivery.tracking.grpc;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.sql.SQLTransientException;

/**
 * Перетворення серверних винятків на gRPC-статуси.
 * <p>
 * Тимчасова недоступність залежності (БД tracking-service або shipment-service)
 * повертається як {@code UNAVAILABLE}: саме цей статус клієнт вважає повторюваним
 * (retryableStatusCodes), тож виклик буде повторено автоматично.
 * Решта неочікуваних помилок — {@code INTERNAL}, їх повторювати немає сенсу.
 */
final class GrpcStatusMapper {

    private GrpcStatusMapper() {
    }

    static StatusRuntimeException toStatus(Throwable error) {
        if (isTransient(error)) {
            return Status.UNAVAILABLE
                    .withDescription("tracking dependency is temporarily unavailable")
                    .withCause(error)
                    .asRuntimeException();
        }

        return Status.INTERNAL
                .withDescription("internal tracking error")
                .withCause(error)
                .asRuntimeException();
    }

    static boolean isTransient(Throwable error) {
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
