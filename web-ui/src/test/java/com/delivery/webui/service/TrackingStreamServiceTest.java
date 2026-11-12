package com.delivery.webui.service;

import com.delivery.tracking.grpc.ShipmentLocationUpdate;
import com.delivery.webui.grpc.ShipmentLocationGrpcClient;
import com.delivery.webui.grpc.TrackingLocationEventMapper;
import com.delivery.webui.grpc.dto.TrackingLocationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackingStreamServiceTest {

    @Mock
    private ShipmentLocationGrpcClient grpcClient;

    @Mock
    private TrackingLocationEventMapper eventMapper;

    @Mock
    private TrackingSseEmitterFactory emitterFactory;

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private SseEmitter emitter;

    @Mock
    private ShipmentLocationGrpcClient.Subscription subscription;

    @Mock
    private ScheduledFuture<Object> heartbeatTask;

    private final CallbackCapture callbacks = new CallbackCapture();
    private ShipmentLocationGrpcClient.Listener grpcListener;
    private TrackingStreamService service;

    @BeforeEach
    void setUp() {
        captureEmitterCallbacks(emitter, callbacks);
        when(emitterFactory.create(Duration.ofMinutes(5)))
                .thenReturn(emitter);
        lenient().when(grpcClient.subscribe(eq(15L), any()))
                .thenAnswer(invocation -> {
                    grpcListener = invocation.getArgument(1);
                    return subscription;
                });
        lenient().doReturn(heartbeatTask).when(taskScheduler).scheduleAtFixedRate(
                any(),
                eq(Duration.ofSeconds(15))
        );
        service = new TrackingStreamService(
                grpcClient,
                eventMapper,
                emitterFactory,
                taskScheduler,
                Duration.ofMinutes(5),
                Duration.ofSeconds(15)
        );
    }

    @Test
    void sendsMappedLocationAsNamedSseEvent() throws Exception {
        ShipmentLocationUpdate update = ShipmentLocationUpdate.newBuilder()
                .setLocationId(11)
                .setShipmentId(15)
                .build();
        TrackingLocationEvent event = new TrackingLocationEvent(
                11,
                15,
                50.455,
                30.53,
                LocalDateTime.of(2026, 11, 12, 13, 0)
        );
        when(eventMapper.toEvent(15, update)).thenReturn(event);

        service.subscribe(15);
        grpcListener.onNext(update);

        SseEmitter.SseEventBuilder sent = captureSentEvent(emitter);
        assertEvent(sent, "location", event);
    }

    @Test
    void cancelsGrpcWhenBrowserDisconnects() {
        service.subscribe(15);

        callbacks.completion.run();

        verify(subscription).cancel();
        verify(heartbeatTask).cancel(false);
    }

    @Test
    void cancelsGrpcAndCompletesEmitterOnTimeout() {
        service.subscribe(15);

        callbacks.timeout.run();

        verify(subscription).cancel();
        verify(heartbeatTask).cancel(false);
        verify(emitter).complete();
    }

    @Test
    void sendsCompletionEventAndClosesSse() throws Exception {
        service.subscribe(15);

        grpcListener.onCompleted();

        assertEvent(
                captureSentEvent(emitter),
                "complete",
                "Location stream completed"
        );
        verify(subscription).cancel();
        verify(heartbeatTask).cancel(false);
        verify(emitter).complete();
    }

    @Test
    void sendsErrorEventAndClosesSse() throws Exception {
        service.subscribe(15);

        grpcListener.onError(new RuntimeException("gRPC unavailable"));

        assertEvent(
                captureSentEvent(emitter),
                "stream-error",
                "Location stream unavailable"
        );
        verify(subscription).cancel();
        verify(heartbeatTask).cancel(false);
        verify(emitter).complete();
    }

    @Test
    void keepsMultipleBrowserConnectionsIndependent() {
        SseEmitter secondEmitter = org.mockito.Mockito.mock(SseEmitter.class);
        ShipmentLocationGrpcClient.Subscription secondSubscription =
                org.mockito.Mockito.mock(
                        ShipmentLocationGrpcClient.Subscription.class
                );
        CallbackCapture secondCallbacks = new CallbackCapture();
        captureEmitterCallbacks(secondEmitter, secondCallbacks);
        when(emitterFactory.create(Duration.ofMinutes(5)))
                .thenReturn(emitter, secondEmitter);
        when(grpcClient.subscribe(eq(15L), any()))
                .thenReturn(subscription, secondSubscription);

        service.subscribe(15);
        service.subscribe(15);
        callbacks.completion.run();

        verify(subscription).cancel();
        verify(secondSubscription, never()).cancel();

        secondCallbacks.completion.run();
        verify(secondSubscription).cancel();
        verify(grpcClient, times(2)).subscribe(eq(15L), any());
    }

    @Test
    void cancelsGrpcWhenBrowserConnectionReportsError() {
        service.subscribe(15);

        callbacks.error.accept(new IOExceptionForTest());

        verify(subscription).cancel();
        verify(heartbeatTask).cancel(false);
        verify(emitter, never()).completeWithError(any());
    }

    @Test
    void heartbeatDetectsDisconnectedBrowserAndCleansResources()
            throws Exception {
        ArgumentCaptor<Runnable> heartbeatCaptor =
                ArgumentCaptor.forClass(Runnable.class);
        doReturn(heartbeatTask).when(taskScheduler).scheduleAtFixedRate(
                heartbeatCaptor.capture(),
                eq(Duration.ofSeconds(15))
        );
        doNothing()
                .doThrow(new IOException("browser disconnected"))
                .when(emitter)
                .send(any(SseEmitter.SseEventBuilder.class));

        service.subscribe(15);
        heartbeatCaptor.getValue().run();

        verify(subscription).cancel();
        verify(heartbeatTask).cancel(false);
        verify(emitter).completeWithError(any(IOException.class));
    }

    private void captureEmitterCallbacks(
            SseEmitter target,
            CallbackCapture capture
    ) {
        doAnswer(invocation -> {
            capture.completion = invocation.getArgument(0);
            return null;
        }).when(target).onCompletion(any());
        doAnswer(invocation -> {
            capture.timeout = invocation.getArgument(0);
            return null;
        }).when(target).onTimeout(any());
        doAnswer(invocation -> {
            capture.error = invocation.getArgument(0);
            return null;
        }).when(target).onError(any());
    }

    private SseEmitter.SseEventBuilder captureSentEvent(SseEmitter target)
            throws Exception {
        ArgumentCaptor<SseEmitter.SseEventBuilder> captor =
                ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(target, atLeastOnce()).send(captor.capture());
        return captor.getAllValues().stream()
                .filter(builder -> builder.build().stream()
                        .map(item -> item.getData())
                        .anyMatch(data -> data instanceof String value
                                && value.startsWith("event:")))
                .findFirst()
                .orElseThrow();
    }

    private void assertEvent(
            SseEmitter.SseEventBuilder builder,
            String eventName,
            Object eventData
    ) {
        Set<Object> data = builder.build().stream()
                .map(item -> item.getData())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(data).contains(
                "event:" + eventName + "\ndata:",
                eventData
        );
    }

    private static final class CallbackCapture {

        private Runnable completion;
        private Runnable timeout;
        private Consumer<Throwable> error;
    }

    private static final class IOExceptionForTest extends RuntimeException {
    }
}
