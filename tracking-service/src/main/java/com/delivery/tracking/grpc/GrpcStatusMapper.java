package com.delivery.tracking.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;

import java.sql.SQLTransientException;

/**
 * Перетворення серверних винятків на gRPC-статуси.
 * <p>
 * Тимчасова недоступність сховища повертається як {@code UNAVAILABLE}: саме цей статус
 * клієнт вважає повторюваним (retryableStatusCodes), тож виклик буде повторено автоматично.
 * Решта неочікуваних помилок — {@code INTERNAL}, їх повторювати немає сенсу.
 */
final class GrpcStatusMapper {

    private GrpcStatusMapper() {
    }

    static StatusRuntimeException toStatus(Throwable error) {
        if (isTransient(error)) {
            return Status.UNAVAILABLE
                    .withDescription("tracking storage is temporarily unavailable")
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
                    || current instanceof SQLTransientException) {
                return true;
            }
        }
        return false;
    }
}
