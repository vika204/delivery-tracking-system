package com.delivery.webui.service;

import com.delivery.webui.client.DispatchCourierClient;
import com.delivery.webui.client.TrackingLocationClient;
import com.delivery.webui.client.TrackingShipmentClient;
import com.delivery.webui.client.dto.DispatchCourierResponse;
import com.delivery.webui.client.dto.ShipmentDetailsResponse;
import com.delivery.webui.client.dto.TrackingLocationResponse;
import com.delivery.webui.client.dto.TrackingShipmentResponse;
import com.delivery.webui.viewmodel.TrackingPageViewModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackingViewServiceTest {

    @Mock
    private TrackingShipmentClient shipmentClient;

    @Mock
    private TrackingLocationClient locationClient;

    @Mock
    private DispatchCourierClient courierClient;

    @Test
    void displaysRealDestinationAndCourierName() {
        when(shipmentClient.getShipmentById(15L))
                .thenReturn(new TrackingShipmentResponse(15L, null, "IN_TRANSIT"));
        when(shipmentClient.getShipmentDetails(15L))
                .thenReturn(new ShipmentDetailsResponse(
                        15L,
                        "  Kyiv, Delivery Street 2  "
                ));
        when(locationClient.getShipmentLocations(15, 0, 100))
                .thenReturn(List.of());
        when(courierClient.getShipmentCourier(15L))
                .thenReturn(new DispatchCourierResponse(
                        15L,
                        true,
                        "  Andrii Kovalenko  "
                ));

        TrackingPageViewModel result = service().getLiveTracking(15);

        assertThat(result.destinationText())
                .isEqualTo("Kyiv, Delivery Street 2");
        assertThat(result.courierText()).isEqualTo("Andrii Kovalenko");
    }

    @Test
    void showsNotAssignedWhenDispatchConfirmsNoAssignment() {
        when(shipmentClient.getShipmentById(15L))
                .thenReturn(new TrackingShipmentResponse(15L, null, "IN_TRANSIT"));
        when(courierClient.getShipmentCourier(15L))
                .thenReturn(new DispatchCourierResponse(15L, false, null));
        when(locationClient.getShipmentLocations(15, 0, 100))
                .thenReturn(List.of());

        assertThat(service().getLiveTracking(15).courierText())
                .isEqualTo("Not assigned");
    }

    @Test
    void showsNotAvailableWhenAssignedCourierHasNoName() {
        when(shipmentClient.getShipmentById(15L))
                .thenReturn(new TrackingShipmentResponse(15L, null, "IN_TRANSIT"));
        when(courierClient.getShipmentCourier(15L))
                .thenReturn(new DispatchCourierResponse(15L, true, "  "));
        when(locationClient.getShipmentLocations(15, 0, 100))
                .thenReturn(List.of());

        assertThat(service().getLiveTracking(15).courierText())
                .isEqualTo("Not available");
    }

    @Test
    void keepsTrackingAvailableWhenDispatchIsUnavailable() {
        when(shipmentClient.getShipmentById(15L))
                .thenReturn(new TrackingShipmentResponse(15L, null, "IN_TRANSIT"));
        when(courierClient.getShipmentCourier(15L))
                .thenThrow(new ResourceAccessException("Connection refused"));
        when(locationClient.getShipmentLocations(15, 0, 100))
                .thenReturn(List.of(location(1, "50.4501", "30.5234")));

        TrackingPageViewModel result = service().getLiveTracking(15);

        assertThat(result.courierText()).isEqualTo("Not available");
        assertThat(result.locations()).hasSize(1);
    }

    @Test
    void keepsCardAvailableWhenShipmentDetailsCannotBeRead() {
        when(shipmentClient.getShipmentById(15L))
                .thenReturn(new TrackingShipmentResponse(15L, null, "IN_TRANSIT"));
        when(shipmentClient.getShipmentDetails(15L))
                .thenThrow(new ResourceAccessException("Connection refused"));
        when(locationClient.getShipmentLocations(15, 0, 100))
                .thenReturn(List.of());

        TrackingPageViewModel result = service().getLiveTracking(15);

        assertThat(result.destinationText()).isEqualTo("Not available");
        assertThat(result.courierText()).isEqualTo("Not available");
    }

    @Test
    void readsLocationHistoryPageByPageUsingLastLocationId() {
        when(shipmentClient.getShipmentById(15L))
                .thenReturn(new TrackingShipmentResponse(15L, null, "IN_TRANSIT"));
        when(locationClient.getShipmentLocations(15, 0, 100))
                .thenReturn(locations(1, 100));
        when(locationClient.getShipmentLocations(15, 100, 100))
                .thenReturn(locations(101, 101));

        TrackingPageViewModel result = service().getLiveTracking(15);

        assertThat(result.locations()).hasSize(101);
        assertThat(result.locations().getFirst().locationId()).isEqualTo(1);
        assertThat(result.locations().getLast().locationId()).isEqualTo(101);
        assertThat(result.locationHistoryComplete()).isTrue();

        InOrder requests = inOrder(locationClient);
        requests.verify(locationClient).getShipmentLocations(15, 0, 100);
        requests.verify(locationClient).getShipmentLocations(15, 100, 100);
        requests.verifyNoMoreInteractions();
    }

    @Test
    void marksHistoryIncompleteWhenBoundedLoadHasMoreLocations() {
        when(shipmentClient.getShipmentById(15L))
                .thenReturn(new TrackingShipmentResponse(15L, null, "IN_TRANSIT"));
        when(locationClient.getShipmentLocations(15, 0, 100))
                .thenReturn(locations(1, 100));
        when(locationClient.getShipmentLocations(15, 100, 100))
                .thenReturn(locations(101, 200));
        when(locationClient.getShipmentLocations(15, 200, 100))
                .thenReturn(locations(201, 300));
        when(locationClient.getShipmentLocations(15, 300, 100))
                .thenReturn(locations(301, 400));
        when(locationClient.getShipmentLocations(15, 400, 100))
                .thenReturn(locations(401, 500));
        when(locationClient.getShipmentLocations(15, 500, 1))
                .thenReturn(locations(501, 501));

        TrackingPageViewModel result = service().getLiveTracking(15);

        assertThat(result.locations()).hasSize(500);
        assertThat(result.locationHistoryComplete()).isFalse();
        assertThat(result.locationMessage()).isEqualTo("LOCATION HISTORY INCOMPLETE");
        assertThat(result.locationDescription()).contains("latest courier position is not available");
    }

    @Test
    void filtersInvalidCoordinatesBeforeBuildingViewModel() {
        when(shipmentClient.getShipmentById(15L))
                .thenReturn(new TrackingShipmentResponse(15L, null, "IN_TRANSIT"));
        when(locationClient.getShipmentLocations(15, 0, 100))
                .thenReturn(List.of(
                        location(1, "50.4501", "30.5234"),
                        location(2, "91", "30"),
                        location(3, "50", "181")
                ));

        TrackingPageViewModel result = service().getLiveTracking(15);

        assertThat(result.locations())
                .extracting(TrackingLocationResponse::locationId)
                .containsExactly(1L);
    }

    @Test
    void doesNotExposeLocationsReturnedForAnotherShipment() {
        when(shipmentClient.getShipmentById(15L))
                .thenReturn(new TrackingShipmentResponse(15L, null, "IN_TRANSIT"));
        when(locationClient.getShipmentLocations(15, 0, 100))
                .thenReturn(List.of(new TrackingLocationResponse(
                        1L,
                        99L,
                        new BigDecimal("50.4501"),
                        new BigDecimal("30.5234"),
                        LocalDateTime.of(2026, 11, 12, 9, 30)
                )));

        TrackingPageViewModel result = service().getLiveTracking(15);

        assertThat(result.locations()).isEmpty();
        assertThat(result.locationDataAvailable()).isFalse();
        assertThat(result.locationMessage())
                .isEqualTo("LOCATION DATA TEMPORARILY UNAVAILABLE");
    }

    @Test
    void marksDeliveredAndCancelledShipmentsAsTerminal() {
        when(shipmentClient.getShipmentById(15L))
                .thenReturn(new TrackingShipmentResponse(15L, null, "DELIVERED"));
        when(shipmentClient.getShipmentById(16L))
                .thenReturn(new TrackingShipmentResponse(16L, null, "cancelled"));
        when(locationClient.getShipmentLocations(15, 0, 100))
                .thenReturn(List.of());
        when(locationClient.getShipmentLocations(16, 0, 100))
                .thenReturn(List.of());

        assertThat(service().getLiveTracking(15).terminal()).isTrue();
        assertThat(service().getLiveTracking(16).terminal()).isTrue();
    }

    @Test
    void keepsActiveShipmentNonTerminal() {
        when(shipmentClient.getShipmentById(15L))
                .thenReturn(new TrackingShipmentResponse(15L, null, "IN_TRANSIT"));
        when(locationClient.getShipmentLocations(15, 0, 100))
                .thenReturn(List.of());

        assertThat(service().getLiveTracking(15).terminal()).isFalse();
    }

    private TrackingViewService service() {
        return new TrackingViewService(
                shipmentClient,
                locationClient,
                courierClient
        );
    }

    private List<TrackingLocationResponse> locations(long firstId, long lastId) {
        return LongStream.rangeClosed(firstId, lastId)
                .mapToObj(id -> location(id, "50.4501", "30.5234"))
                .toList();
    }

    private TrackingLocationResponse location(
            long locationId,
            String latitude,
            String longitude
    ) {
        return new TrackingLocationResponse(
                locationId,
                15L,
                new BigDecimal(latitude),
                new BigDecimal(longitude),
                LocalDateTime.of(2026, 11, 12, 9, 30)
        );
    }
}
