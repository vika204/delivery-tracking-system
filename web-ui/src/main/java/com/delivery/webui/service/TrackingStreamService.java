package com.delivery.webui.service;

import com.delivery.tracking.grpc.ShipmentLocationUpdate;
import com.delivery.webui.grpc.ShipmentLocationGrpcClient;
import com.delivery.webui.grpc.TrackingLocationEventMapper;
import com.delivery.webui.grpc.dto.TrackingLocationEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
@ConditionalOnProperty(
        prefix = "tracking.stream",
        name = "enabled",
        havingValue = "true"
)
public class TrackingStreamService {

    private final ShipmentLocationGrpcClient grpcClient;
    private final TrackingLocationEventMapper eventMapper;
    private final TrackingSseEmitterFactory emitterFactory;
    private final TaskScheduler taskScheduler;
    private final Duration timeout;
    private final Duration heartbeat;

    public TrackingStreamService(
            ShipmentLocationGrpcClient grpcClient,
            TrackingLocationEventMapper eventMapper,
            TrackingSseEmitterFactory emitterFactory,
            TaskScheduler taskScheduler,
            @Value("${tracking.stream.timeout}") Duration timeout,
            @Value("${tracking.stream.heartbeat}") Duration heartbeat
    ) {
        this.grpcClient = grpcClient;
        this.eventMapper = eventMapper;
        this.emitterFactory = emitterFactory;
        this.taskScheduler = taskScheduler;
        this.timeout = timeout;
        this.heartbeat = heartbeat;
    }

    public SseEmitter subscribe(long shipmentId) {
        SseEmitter emitter = emitterFactory.create(timeout);
        StreamConnection connection =
                new StreamConnection(shipmentId, emitter);

        emitter.onCompletion(connection::browserDisconnected);
        emitter.onTimeout(connection::timedOut);
        emitter.onError(connection::browserError);

        try {
            connection.startHeartbeat();

            if (connection.isClosed()) {
                return emitter;
            }

            connection.attach(grpcClient.subscribe(shipmentId, connection));
        } catch (RuntimeException ex) {
            connection.grpcError(ex);
        }

        return emitter;
    }

    private final class StreamConnection
            implements ShipmentLocationGrpcClient.Listener {

        private final long shipmentId;
        private final SseEmitter emitter;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicReference<ShipmentLocationGrpcClient.Subscription>
                subscription = new AtomicReference<>();
        private final AtomicReference<ScheduledFuture<?>> heartbeatTask =
                new AtomicReference<>();

        private StreamConnection(long shipmentId, SseEmitter emitter) {
            this.shipmentId = shipmentId;
            this.emitter = emitter;
        }

        private boolean isClosed() {
            return closed.get();
        }

        private void startHeartbeat() {
            sendHeartbeat();

            if (closed.get()) {
                return;
            }

            ScheduledFuture<?> scheduled = taskScheduler.scheduleAtFixedRate(
                    this::sendHeartbeat,
                    heartbeat
            );

            if (!heartbeatTask.compareAndSet(null, scheduled)) {
                scheduled.cancel(false);
                throw new IllegalStateException("SSE heartbeat already scheduled");
            }

            if (closed.get()) {
                cancelHeartbeat();
            }
        }

        private void sendHeartbeat() {
            if (closed.get()) {
                return;
            }

            try {
                emitter.send(SseEmitter.event().comment("keepalive"));
            } catch (IOException ex) {
                fail(ex);
            }
        }

        private void attach(
                ShipmentLocationGrpcClient.Subscription grpcSubscription
        ) {
            if (!subscription.compareAndSet(null, grpcSubscription)) {
                grpcSubscription.cancel();
                throw new IllegalStateException("gRPC subscription already attached");
            }

            if (closed.get()) {
                cancelGrpcSubscription();
            }
        }

        @Override
        public void onNext(ShipmentLocationUpdate update) {
            if (closed.get()) {
                return;
            }

            try {
                TrackingLocationEvent event =
                        eventMapper.toEvent(shipmentId, update);
                emitter.send(SseEmitter.event()
                        .name("location")
                        .data(event));
            } catch (IOException | IllegalArgumentException ex) {
                fail(ex);
            }
        }

        @Override
        public void onError(Throwable error) {
            grpcError(error);
        }

        @Override
        public void onCompleted() {
            closeWithEvent("complete", "Location stream completed");
        }

        private void grpcError(Throwable error) {
            closeWithEvent("stream-error", "Location stream unavailable");
        }

        private void browserDisconnected() {
            closeWithoutCompletingEmitter();
        }

        private void browserError(Throwable error) {
            closeWithoutCompletingEmitter();
        }

        private void timedOut() {
            if (closed.compareAndSet(false, true)) {
                cancelHeartbeat();
                cancelGrpcSubscription();
                emitter.complete();
            }
        }

        private void closeWithEvent(String eventName, String message) {
            if (!closed.compareAndSet(false, true)) {
                return;
            }

            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(message));
            } catch (IOException ignored) {
                // The connection may already be gone; cleanup still continues.
            } finally {
                cancelHeartbeat();
                cancelGrpcSubscription();
                emitter.complete();
            }
        }

        private void fail(Throwable error) {
            if (closed.compareAndSet(false, true)) {
                cancelHeartbeat();
                cancelGrpcSubscription();
                emitter.completeWithError(error);
            }
        }

        private void closeWithoutCompletingEmitter() {
            if (closed.compareAndSet(false, true)) {
                cancelHeartbeat();
                cancelGrpcSubscription();
            }
        }

        private void cancelHeartbeat() {
            ScheduledFuture<?> current = heartbeatTask.getAndSet(null);

            if (current != null) {
                current.cancel(false);
            }
        }

        private void cancelGrpcSubscription() {
            ShipmentLocationGrpcClient.Subscription current =
                    subscription.getAndSet(null);

            if (current != null) {
                current.cancel();
            }
        }
    }
}
