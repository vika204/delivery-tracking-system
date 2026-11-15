package com.delivery.notification.grpc;

import io.grpc.Status;

/**
 * Помилка gRPC-виклику tracking-service після вичерпання всіх retries.
 */
public class TrackingGrpcException extends RuntimeException {

    private final Status.Code code;

    public TrackingGrpcException(Status status, Throwable cause) {
        super("tracking-service gRPC call failed: " + status.getCode()
                + (status.getDescription() == null ? "" : " (" + status.getDescription() + ")"), cause);
        this.code = status.getCode();
    }

    public Status.Code getCode() {
        return code;
    }

    public boolean isUnavailable() {
        return code == Status.Code.UNAVAILABLE;
    }
}
