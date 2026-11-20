package com.delivery.notification.grpc;

import com.delivery.tracking.api.ProtoTimestamps;
import com.delivery.tracking.grpc.GetShipmentDeliveryInfoRequest;
import com.delivery.tracking.grpc.ShipmentDeliveryInfo;
import com.delivery.tracking.grpc.ShipmentTrackingServiceGrpc;
import com.delivery.tracking.grpcclient.TrackingGrpcException;
import com.delivery.tracking.grpcclient.TrackingGrpcProperties;
import io.grpc.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrackingGrpcClientTest {

    private final ShipmentTrackingServiceGrpc.ShipmentTrackingServiceBlockingStub stub =
            mock(ShipmentTrackingServiceGrpc.ShipmentTrackingServiceBlockingStub.class);
    private TrackingGrpcClient client;

    @BeforeEach
    void setUp() {
        var properties = new TrackingGrpcProperties(Duration.ofSeconds(5),
                new TrackingGrpcProperties.Retry(3, Duration.ofMillis(10), Duration.ofMillis(100), 2));
        when(stub.withDeadlineAfter(5000, TimeUnit.MILLISECONDS)).thenReturn(stub);
        client = new TrackingGrpcClient(stub, null, properties);
    }

    @AfterEach
    void tearDown() {
        client.shutdown();
    }

    @Test
    void unaryReturnsLatestLocationAndStatus() {
        Instant recordedAt = Instant.parse("2026-11-19T10:00:00Z");
        when(stub.getShipmentDeliveryInfo(GetShipmentDeliveryInfoRequest.newBuilder().setShipmentId(15).build()))
                .thenReturn(ShipmentDeliveryInfo.newBuilder().setShipmentId(15).setStatus("IN_TRANSIT")
                        .setHasLocation(true).setLatitude(50.45).setLongitude(30.52)
                        .setRecordedAt(ProtoTimestamps.toTimestamp(recordedAt)).build());

        var response = client.getDeliveryInfo(15);

        assertThat(response.shipmentId()).isEqualTo(15);
        assertThat(response.status()).isEqualTo("IN_TRANSIT");
        assertThat(response.latitude()).isEqualTo(50.45);
        assertThat(response.longitude()).isEqualTo(30.52);
        assertThat(response.recordedAt()).isEqualTo(recordedAt);
    }

    @Test
    void unaryDoesNotInventCoordinatesWhenLocationIsAbsent() {
        when(stub.getShipmentDeliveryInfo(any())).thenReturn(ShipmentDeliveryInfo.newBuilder()
                .setShipmentId(15).setStatus("ASSIGNED").build());

        var response = client.getDeliveryInfo(15);

        assertThat(response.status()).isEqualTo("ASSIGNED");
        assertThat(response.latitude()).isNull();
        assertThat(response.longitude()).isNull();
        assertThat(response.recordedAt()).isNull();
    }

    @Test
    void unaryPreservesUnavailableStatusForHttpErrorHandling() {
        when(stub.getShipmentDeliveryInfo(any())).thenThrow(Status.UNAVAILABLE.asRuntimeException());

        assertThatThrownBy(() -> client.getDeliveryInfo(15))
                .isInstanceOfSatisfying(TrackingGrpcException.class,
                        error -> assertThat(error.getCode()).isEqualTo(Status.Code.UNAVAILABLE));
    }
}
