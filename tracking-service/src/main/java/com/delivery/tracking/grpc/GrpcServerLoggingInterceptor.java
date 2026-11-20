package com.delivery.tracking.grpc;

import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.stereotype.Component;

@Component
@GlobalServerInterceptor
public class GrpcServerLoggingInterceptor implements ServerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GrpcServerLoggingInterceptor.class);

    @Override
    public <Q, R> ServerCall.Listener<Q> interceptCall(
            ServerCall<Q, R> call,
            Metadata headers,
            ServerCallHandler<Q, R> next
    ) {
        String methodName = call.getMethodDescriptor().getBareMethodName();
        long startedAt = System.nanoTime();

        log.info("gRPC server call started: method={}", methodName);

        return next.startCall(new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
            @Override
            public void close(Status status, Metadata trailers) {
                long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
                if (status.isOk()) {
                    log.info("gRPC server call completed: method={}, status=OK, elapsedMs={}",
                            methodName, elapsedMs);
                } else {
                    log.warn("gRPC server call completed: method={}, status={}, description={}, elapsedMs={}",
                            methodName, status.getCode(), status.getDescription(), elapsedMs);
                }
                super.close(status, trailers);
            }
        }, headers);
    }
}
