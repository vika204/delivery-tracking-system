package com.delivery.webui.service;

import com.delivery.tracking.grpc.ShipmentLocationUpdate;
import com.delivery.webui.grpc.ShipmentLocationGrpcClient;
import com.delivery.webui.grpc.TrackingLocationEventMapper;
import com.delivery.webui.grpc.dto.TrackingLocationEvent;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

@Service
@ConditionalOnProperty(
        prefix = "tracking.stream",
        name = "enabled",
        havingValue = "true"
)
public class TrackingStreamService {

    private final ShipmentLocationGrpcClient grpcClient;
    private final TrackingLocationEventMapper eventMapper;
    private final TaskScheduler taskScheduler;
    private final Duration timeout;
    private final Duration heartbeat;

    public TrackingStreamService(
            ShipmentLocationGrpcClient grpcClient,
            TrackingLocationEventMapper eventMapper,
            TaskScheduler taskScheduler,
            @Value("${tracking.stream.timeout}") Duration timeout,
            @Value("${tracking.stream.heartbeat}") Duration heartbeat
    ) {
        this.grpcClient = grpcClient;
        this.eventMapper = eventMapper;
        this.taskScheduler = taskScheduler;
        this.timeout = timeout;
        this.heartbeat = heartbeat;
    }

    public SseEmitter subscribe(long shipmentId) {
        SseEmitter emitter = new SseEmitter(timeout.toMillis());
        StreamConnection connection = new StreamConnection(shipmentId, emitter);

        emitter.onCompletion(connection::close);
        emitter.onTimeout(connection::timeout);
        emitter.onError(error -> connection.close());

        try {
            connection.startHeartbeat();

            if (connection.isClosed()) {
                return emitter;
            }

            Context.CancellableContext grpcContext =
                    grpcClient.subscribe(shipmentId, connection);
            connection.attach(grpcContext);
        } catch (RuntimeException ex) {
            connection.closeWithEvent(
                    "stream-error",
                    "Location stream unavailable"
            );
        }

        return emitter;
    }

    private final class StreamConnection
            implements StreamObserver<ShipmentLocationUpdate> {

        private final long shipmentId;
        private final SseEmitter emitter;
        private boolean closed;
        private Context.CancellableContext grpcContext;
        private ScheduledFuture<?> heartbeatTask;

        private StreamConnection(long shipmentId, SseEmitter emitter) {
            this.shipmentId = shipmentId;
            this.emitter = emitter;
        }

        private synchronized boolean isClosed() {
            return closed;
        }

        private void startHeartbeat() {
            sendHeartbeat();
            if (isClosed()) {
                return;
            }

            ScheduledFuture<?> scheduled = taskScheduler.scheduleAtFixedRate(
                    this::sendHeartbeat,
                    heartbeat
            );

            synchronized (this) {
                if (!closed) {
                    heartbeatTask = scheduled;
                    return;
                }
            }

            scheduled.cancel(false);
        }

        private void sendHeartbeat() {
            if (isClosed()) {
                return;
            }

            try {
                emitter.send(SseEmitter.event().comment("keepalive"));
            } catch (IOException ex) {
                fail(ex);
            }
        }

        private void attach(Context.CancellableContext context) {
            synchronized (this) {
                if (!closed) {
                    grpcContext = context;
                    return;
                }
            }

            context.cancel(null);
        }

        @Override
        public void onNext(ShipmentLocationUpdate update) {
            if (isClosed()) {
                return;
            }

            try {
                TrackingLocationEvent event = eventMapper.toEvent(shipmentId, update);
                emitter.send(SseEmitter.event()
                        .name("location")
                        .data(event));
            } catch (IOException | IllegalArgumentException ex) {
                fail(ex);
            }
        }

        @Override
        public void onError(Throwable error) {
            closeWithEvent("stream-error", "Location stream unavailable");
        }

        @Override
        public void onCompleted() {
            closeWithEvent("complete", "Location stream completed");
        }

        private void timeout() {
            if (close()) {
                emitter.complete();
            }
        }

        private void closeWithEvent(String eventName, String message) {
            if (!close()) {
                return;
            }

            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(message));
            } catch (IOException ignored) {
                // The connection may already be gone; cleanup still continues.
            } finally {
                emitter.complete();
            }
        }

        private void fail(Throwable error) {
            if (close()) {
                emitter.completeWithError(error);
            }
        }

        private boolean close() {
            ScheduledFuture<?> heartbeatToCancel;
            Context.CancellableContext grpcToCancel;

            synchronized (this) {
                if (closed) {
                    return false;
                }

                closed = true;
                heartbeatToCancel = heartbeatTask;
                heartbeatTask = null;
                grpcToCancel = grpcContext;
                grpcContext = null;
            }

            if (heartbeatToCancel != null) {
                heartbeatToCancel.cancel(false);
            }

            if (grpcToCancel != null) {
                grpcToCancel.cancel(null);
            }

            return true;
        }
    }
}
