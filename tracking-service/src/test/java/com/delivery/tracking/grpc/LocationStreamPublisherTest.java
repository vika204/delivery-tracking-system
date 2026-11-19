package com.delivery.tracking.grpc;

import com.delivery.tracking.event.LocationSavedEvent;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCallStreamObserver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocationStreamPublisherTest {

    private final LocationStreamPublisher publisher = new LocationStreamPublisher(new TrackingProtoMapper("UTC"));

    @Test
    void shutdownClosesOpenStreamsAsUnavailable() {
        ServerCallStreamObserver<ShipmentLocationUpdate> first = observer();
        ServerCallStreamObserver<ShipmentLocationUpdate> second = observer();
        publisher.subscribe(15, first);
        publisher.subscribe(16, second);

        publisher.closeAllOnShutdown();

        assertUnavailable(first);
        assertUnavailable(second);
    }

    @Test
    void shutdownSkipsStreamsAlreadyCancelledByClient() {
        ServerCallStreamObserver<ShipmentLocationUpdate> cancelled = observer();
        publisher.subscribe(15, cancelled);
        when(cancelled.isCancelled()).thenReturn(true);

        publisher.closeAllOnShutdown();

        verify(cancelled, never()).onError(any());
    }

    @Test
    void subscriptionDuringShutdownIsRejectedAndNotRegistered() {
        publisher.closeAllOnShutdown();
        ServerCallStreamObserver<ShipmentLocationUpdate> late = observer();

        publisher.subscribe(15, late);
        publisher.onLocationSaved(new LocationSavedEvent(1L, 15L, new BigDecimal("50.45"), new BigDecimal("30.52"),
                LocalDateTime.of(2026, 11, 19, 10, 0)));

        assertUnavailable(late);
        verify(late, never()).onNext(any());
    }

    @Test
    void liveStreamsKeepReceivingPointsBeforeShutdown() {
        ServerCallStreamObserver<ShipmentLocationUpdate> live = observer();
        publisher.subscribe(15, live);

        publisher.onLocationSaved(new LocationSavedEvent(7L, 15L, new BigDecimal("50.45"), new BigDecimal("30.52"),
                LocalDateTime.of(2026, 11, 19, 10, 0)));

        ArgumentCaptor<ShipmentLocationUpdate> update = ArgumentCaptor.forClass(ShipmentLocationUpdate.class);
        verify(live).onNext(update.capture());
        assertThat(update.getValue().getLocationId()).isEqualTo(7L);
    }

    @SuppressWarnings("unchecked")
    private static ServerCallStreamObserver<ShipmentLocationUpdate> observer() {
        return mock(ServerCallStreamObserver.class);
    }

    private static void assertUnavailable(ServerCallStreamObserver<ShipmentLocationUpdate> observer) {
        ArgumentCaptor<Throwable> error = ArgumentCaptor.forClass(Throwable.class);
        verify(observer).onError(error.capture());
        assertThat(error.getValue()).isInstanceOf(StatusRuntimeException.class);
        assertThat(Status.fromThrowable(error.getValue()).getCode()).isEqualTo(Status.Code.UNAVAILABLE);
    }
}
