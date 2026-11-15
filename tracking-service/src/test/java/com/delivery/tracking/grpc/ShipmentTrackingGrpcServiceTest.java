package com.delivery.tracking.grpc;

import com.delivery.tracking.client.dto.ShipmentDto;
import com.delivery.tracking.route.ShipmentRouteService;
import com.delivery.tracking.service.LocationService;
import com.delivery.tracking.service.TrackingService;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipmentTrackingGrpcServiceTest {

    @Mock
    private LocationStreamPublisher publisher;

    @Mock
    private TrackingService trackingService;

    @Mock
    private LocationService locationService;

    @Mock
    private ShipmentRouteService routeService;

    @Mock
    private TrackingProtoMapper mapper;

    @Mock
    private ServerCallStreamObserver<ShipmentLocationUpdate> observer;

    @InjectMocks
    private ShipmentTrackingGrpcService grpcService;

    @Test
    void shouldCompleteAlreadyDeliveredShipment() {
        when(trackingService.getShipmentById(15L))
                .thenReturn(new ShipmentDto(15L, "Recipient", "DELIVERED"));

        grpcService.subscribeToShipmentLocations(
                SubscribeToShipmentLocationsRequest.newBuilder().setShipmentId(15L).build(),
                observer
        );

        verify(publisher).subscribe(15L, observer);
        verify(publisher).completeShipment(15L);
    }

    @Test
    void shouldKeepInTransitShipmentOpen() {
        when(trackingService.getShipmentById(15L))
                .thenReturn(new ShipmentDto(15L, "Recipient", "IN_TRANSIT"));

        grpcService.subscribeToShipmentLocations(
                SubscribeToShipmentLocationsRequest.newBuilder().setShipmentId(15L).build(),
                observer
        );

        verify(publisher).subscribe(15L, observer);
        verify(publisher, never()).completeShipment(15L);
    }

    @Test
    void unaryReturnsUnavailableWhenDatabaseIsDown() {
        when(locationService.findLatestLocation(15L))
                .thenThrow(new CannotCreateTransactionException("Connection refused"));

        grpcService.getLatestShipmentLocation(
                GetLatestShipmentLocationRequest.newBuilder().setShipmentId(15L).build(),
                observer
        );

        assertThat(capturedStatus(observer)).isEqualTo(Status.Code.UNAVAILABLE);
    }

    @Test
    void unaryReturnsInternalForUnexpectedError() {
        when(locationService.findLatestLocation(15L))
                .thenThrow(new DataIntegrityViolationException("broken"));

        grpcService.getLatestShipmentLocation(
                GetLatestShipmentLocationRequest.newBuilder().setShipmentId(15L).build(),
                observer
        );

        assertThat(capturedStatus(observer)).isEqualTo(Status.Code.INTERNAL);
    }

    @Test
    @SuppressWarnings("unchecked")
    void clientStreamingReturnsUnavailableWhenDatabaseIsDown() {
        StreamObserver<ShipmentRouteSummary> responseObserver = mock(StreamObserver.class);
        when(routeService.recordRoute(anyString(), anyLong(), anyList()))
                .thenThrow(new CannotCreateTransactionException("Connection refused"));

        ShipmentTrackingGrpcService service = new ShipmentTrackingGrpcService(
                publisher, trackingService, locationService, routeService, new TrackingProtoMapper("UTC"));

        StreamObserver<ShipmentRoutePoint> requestObserver = service.recordShipmentRoute(responseObserver);
        requestObserver.onNext(ShipmentRoutePoint.newBuilder()
                .setUploadId("upload-1")
                .setShipmentId(15L)
                .setLatitude(50.45)
                .setLongitude(30.52)
                .setRecordedAt(Timestamp.newBuilder().setSeconds(1_794_000_000L))
                .build());
        requestObserver.onCompleted();

        assertThat(capturedStatus(responseObserver)).isEqualTo(Status.Code.UNAVAILABLE);
        verify(responseObserver, never()).onNext(any());
    }

    private static Status.Code capturedStatus(StreamObserver<?> observer) {
        ArgumentCaptor<Throwable> error = ArgumentCaptor.forClass(Throwable.class);
        verify(observer).onError(error.capture());
        return Status.fromThrowable(error.getValue()).getCode();
    }
}
