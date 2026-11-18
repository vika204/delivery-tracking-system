package com.delivery.dispatch.exception;

import io.grpc.Status;

public class TrackingGrpcException extends RuntimeException {

    private final Status.Code code;

    public TrackingGrpcException(Throwable cause) {
        this(Status.fromThrowable(cause), cause);
    }

    private TrackingGrpcException(Status status, Throwable cause) {
        super("tracking-service gRPC call failed: " + status.getCode()
                + (status.getDescription() == null ? "" : " (" + status.getDescription() + ")"), cause);
        this.code = status.getCode();
    }

    public Status.Code getCode() {
        return code;
    }
}
