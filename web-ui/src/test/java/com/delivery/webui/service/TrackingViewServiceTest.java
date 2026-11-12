package com.delivery.webui.service;

import com.delivery.webui.client.TrackingLocationClient;
import com.delivery.webui.client.TrackingShipmentClient;
import com.delivery.webui.client.dto.TrackingLocationResponse;
import com.delivery.webui.client.dto.TrackingShipmentResponse;
import com.delivery.webui.viewmodel.TrackingPageViewModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    private TrackingViewService service() {
        return new TrackingViewService(shipmentClient, locationClient);
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
