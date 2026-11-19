package com.delivery.webui.viewmodel;

import com.delivery.webui.client.dto.ClientShipmentResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ParcelViewModelTest {

    @Test
    void missingOptionalDataUsesNeutralLabels() {
        ClientShipmentResponse shipment = new ClientShipmentResponse(515, 42, "WAITING_FOR_COURIER",
                null, null, null, null, null, null, null, null, null, null, null, null, null);

        ParcelViewModel parcel = ParcelViewModel.from(shipment);

        assertThat(parcel.statusLabel()).isEqualTo("WAITING FOR COURIER");
        assertThat(parcel.statusClass()).isEqualTo("waiting");
        assertThat(parcel.courierName()).isEqualTo("Not assigned");
        assertThat(parcel.courierPhone()).isEqualTo("Not available");
        assertThat(parcel.distance()).isEqualTo("Not available");
        assertThat(parcel.createdAt()).isEqualTo("Not available");
    }

    @Test
    void assignedCourierWithMissingNameIsUnavailableAndKilometersStayKilometers() {
        ClientShipmentResponse shipment = new ClientShipmentResponse(515, 42, "ASSIGNED",
                null, null, null, null, null, new BigDecimal("1.20"), null, null, null, null,
                null, 7001L, " ");

        ParcelViewModel parcel = ParcelViewModel.from(shipment);

        assertThat(parcel.courierName()).isEqualTo("Not available");
        assertThat(parcel.distance()).isEqualTo("1.2 km");
        assertThat(parcel.statusLabel()).isEqualTo("ASSIGNED");
        assertThat(parcel.statusClass()).isEqualTo("transit");
    }

    @Test
    void shortDistancesStayInMetersAndNoStatusIsInvented() {
        ClientShipmentResponse shipment = new ClientShipmentResponse(515, 42, null,
                null, null, null, null, null, new BigDecimal("0.02"), null, null, null, null,
                null, null, null);

        ParcelViewModel parcel = ParcelViewModel.from(shipment);

        assertThat(parcel.distance()).isEqualTo("20 m");
        assertThat(parcel.statusLabel()).isEqualTo("NOT AVAILABLE");
        assertThat(parcel.delivered()).isFalse();
    }
}
