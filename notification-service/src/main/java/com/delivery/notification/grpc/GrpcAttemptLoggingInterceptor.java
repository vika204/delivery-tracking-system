package com.delivery.notification.grpc;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientStreamTracer;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.grpc.client.GlobalClientInterceptor;
import org.springframework.stereotype.Component;

/**
 * Логує кожну фізичну спробу gRPC-виклику: retries виконуються всередині каналу,
 * тому їх видно лише через {@link ClientStreamTracer}, створений на кожну спробу.
 */
@Component
@GlobalClientInterceptor
public class GrpcAttemptLoggingInterceptor implements ClientInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GrpcAttemptLoggingInterceptor.class);

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        String methodName = method.getBareMethodName();
        long startedAt = System.nanoTime();

        CallOptions tracedOptions = callOptions.withStreamTracerFactory(new ClientStreamTracer.Factory() {
            @Override
            public ClientStreamTracer newClientStreamTracer(ClientStreamTracer.StreamInfo info, Metadata headers) {
                int attempt = info.getPreviousAttempts() + 1;
                if (attempt > 1) {
                    log.warn("gRPC retry attempt: method={}, attempt={}", methodName, attempt);
                } else {
                    log.info("gRPC call attempt: method={}, attempt={}", methodName, attempt);
                }

                return new ClientStreamTracer() {
                    @Override
                    public void streamClosed(Status status) {
                        log.info("gRPC attempt finished: method={}, attempt={}, status={}",
                                methodName, attempt, status.getCode());
                    }
                };
            }
        });

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, tracedOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(responseListener) {
                    @Override
                    public void onClose(Status status, Metadata trailers) {
                        log.info("gRPC call completed: method={}, status={}, description={}, elapsedMs={}",
                                methodName, status.getCode(), status.getDescription(),
                                (System.nanoTime() - startedAt) / 1_000_000);
                        super.onClose(status, trailers);
                    }
                }, headers);
            }
        };
    }
}
