package com.delivery.tracking.grpc;

import com.delivery.tracking.client.dto.ShipmentDto;
import com.delivery.tracking.service.TrackingService;
import io.grpc.stub.ServerCallStreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}