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
 * Логує кожну фізичну спробу gRPC-виклику та її статус.
 * <p>
 * Звичайний interceptor викликається один раз на логічний виклик, а retries виконуються
 * глибше в каналі. Тому для кожної спроби реєструється {@link ClientStreamTracer}:
 * gRPC створює новий tracer на кожну спробу й передає номер попередніх спроб.
 */
@Component
@GlobalClientInterceptor
public class GrpcAttemptLoggingInterceptor implements ClientInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GrpcAttemptLoggingInterceptor.class);

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next
    ) {
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
                        if (status.isOk()) {
                            log.info("gRPC attempt finished: method={}, attempt={}, status=OK", methodName, attempt);
                        } else {
                            log.warn("gRPC attempt finished: method={}, attempt={}, status={}, description={}",
                                    methodName, attempt, status.getCode(), status.getDescription());
                        }
                    }
                };
            }
        });

        ClientCall<ReqT, RespT> call = next.newCall(method, tracedOptions);

        return new ForwardingClientCall.SimpleForwardingClientCall<>(call) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(responseListener) {
                    @Override
                    public void onClose(Status status, Metadata trailers) {
                        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
                        log.info("gRPC call completed: method={}, status={}, elapsedMs={}",
                                methodName, status.getCode(), elapsedMs);
                        super.onClose(status, trailers);
                    }
                }, headers);
            }
        };
    }
}
