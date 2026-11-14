package com.delivery.webui.service;

import com.delivery.tracking.grpc.ShipmentLocationUpdate;
import com.delivery.webui.grpc.ShipmentLocationGrpcClient;
import com.delivery.webui.grpc.TrackingLocationEventMapper;
import com.delivery.webui.grpc.dto.TrackingLocationEvent;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
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
    private TaskScheduler taskScheduler;

    @Mock
    private ScheduledFuture<Object> heartbeatTask;

    private final List<CallbackCapture> emitterCallbacks = new ArrayList<>();
    private final List<StreamObserver<ShipmentLocationUpdate>> grpcObservers =
            new ArrayList<>();
    private final List<Context.CancellableContext> grpcContexts =
            new ArrayList<>();
    private MockedConstruction<SseEmitter> emitterConstruction;
    private TrackingStreamService service;

    @BeforeEach
    void setUp() {
        emitterConstruction = mockConstruction(
                SseEmitter.class,
                (emitter, context) -> {
                    CallbackCapture callbacks = new CallbackCapture();
                    captureEmitterCallbacks(emitter, callbacks);
                    emitterCallbacks.add(callbacks);
                }
        );

        lenient().when(grpcClient.subscribe(eq(15L), any()))
                .thenAnswer(invocation -> {
                    grpcObservers.add(invocation.getArgument(1));
                    Context.CancellableContext context =
                            Context.current().withCancellation();
                    grpcContexts.add(context);
                    return context;
                });
        lenient().doReturn(heartbeatTask).when(taskScheduler).scheduleAtFixedRate(
                any(),
                eq(Duration.ofSeconds(15))
        );

        service = new TrackingStreamService(
                grpcClient,
                eventMapper,
                taskScheduler,
                Duration.ofMinutes(5),
                Duration.ofSeconds(15)
        );
    }

    @AfterEach
    void tearDown() {
        grpcContexts.forEach(context -> context.cancel(null));
        emitterConstruction.close();
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
        grpcObserver(0).onNext(update);

        assertEvent(
                captureSentEvent(emitter(0)),
                "location",
                event
        );
    }

    @Test
    void cancelsGrpcWhenBrowserDisconnects() {
        service.subscribe(15);

        callbacks(0).completion.run();

        assertThat(grpcContext(0).isCancelled()).isTrue();
        verify(heartbeatTask).cancel(false);
    }

    @Test
    void cancelsGrpcAndCompletesEmitterOnTimeout() {
        service.subscribe(15);

        callbacks(0).timeout.run();

        assertThat(grpcContext(0).isCancelled()).isTrue();
        verify(heartbeatTask).cancel(false);
        verify(emitter(0)).complete();
    }

    @Test
    void sendsCompletionEventAndClosesSse() throws Exception {
        service.subscribe(15);

        grpcObserver(0).onCompleted();

        assertEvent(
                captureSentEvent(emitter(0)),
                "complete",
                "Location stream completed"
        );
        assertThat(grpcContext(0).isCancelled()).isTrue();
        verify(heartbeatTask).cancel(false);
        verify(emitter(0)).complete();
    }

    @Test
    void sendsErrorEventAndClosesSse() throws Exception {
        service.subscribe(15);

        grpcObserver(0).onError(new RuntimeException("gRPC unavailable"));

        assertEvent(
                captureSentEvent(emitter(0)),
                "stream-error",
                "Location stream unavailable"
        );
        assertThat(grpcContext(0).isCancelled()).isTrue();
        verify(heartbeatTask).cancel(false);
        verify(emitter(0)).complete();
    }

    @Test
    void ignoresRepeatedCloseCallbacks() {
        service.subscribe(15);

        grpcObserver(0).onCompleted();
        grpcObserver(0).onError(new RuntimeException("late error"));
        callbacks(0).timeout.run();
        callbacks(0).completion.run();

        assertThat(grpcContext(0).isCancelled()).isTrue();
        verify(heartbeatTask, times(1)).cancel(false);
        verify(emitter(0), times(1)).complete();
        verify(emitter(0), never()).completeWithError(any());
    }

    @Test
    void keepsMultipleBrowserConnectionsIndependent() {
        ScheduledFuture<?> secondHeartbeatTask = mock(ScheduledFuture.class);
        doReturn(heartbeatTask, secondHeartbeatTask)
                .when(taskScheduler)
                .scheduleAtFixedRate(any(), eq(Duration.ofSeconds(15)));

        service.subscribe(15);
        service.subscribe(15);
        callbacks(0).completion.run();

        assertThat(grpcContext(0).isCancelled()).isTrue();
        assertThat(grpcContext(1).isCancelled()).isFalse();
        verify(heartbeatTask).cancel(false);
        verify(secondHeartbeatTask, never()).cancel(false);

        callbacks(1).completion.run();

        assertThat(grpcContext(1).isCancelled()).isTrue();
        verify(secondHeartbeatTask).cancel(false);
        verify(grpcClient, times(2)).subscribe(eq(15L), any());
    }

    @Test
    void cancelsGrpcWhenBrowserConnectionReportsError() {
        service.subscribe(15);

        callbacks(0).error.accept(new IOExceptionForTest());

        assertThat(grpcContext(0).isCancelled()).isTrue();
        verify(heartbeatTask).cancel(false);
        verify(emitter(0), never()).completeWithError(any());
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

        service.subscribe(15);
        doThrow(new IOException("browser disconnected"))
                .when(emitter(0))
                .send(any(SseEmitter.SseEventBuilder.class));
        heartbeatCaptor.getValue().run();

        assertThat(grpcContext(0).isCancelled()).isTrue();
        verify(heartbeatTask).cancel(false);
        verify(emitter(0)).completeWithError(any(IOException.class));
    }

    private SseEmitter emitter(int index) {
        return emitterConstruction.constructed().get(index);
    }

    private CallbackCapture callbacks(int index) {
        return emitterCallbacks.get(index);
    }

    private StreamObserver<ShipmentLocationUpdate> grpcObserver(int index) {
        return grpcObservers.get(index);
    }

    private Context.CancellableContext grpcContext(int index) {
        return grpcContexts.get(index);
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
