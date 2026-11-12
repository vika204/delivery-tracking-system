package com.delivery.webui.grpc;

import com.delivery.tracking.grpc.ShipmentLocationUpdate;
import com.delivery.webui.grpc.dto.TrackingLocationEvent;
import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrackingLocationEventMapperTest {

    private final TrackingLocationEventMapper mapper =
            new TrackingLocationEventMapper();

    @Test
    void mapsProtobufUpdateToSseDtoAndJson() throws Exception {
        ShipmentLocationUpdate update = ShipmentLocationUpdate.newBuilder()
                .setLocationId(11)
                .setShipmentId(15)
                .setLatitude(50.455)
                .setLongitude(30.53)
                .setRecordedAt(Timestamp.newBuilder()
                        .setSeconds(1_794_490_000L)
                        .build())
                .build();

        TrackingLocationEvent event = mapper.toEvent(15, update);

        assertThat(event.locationId()).isEqualTo(11);
        assertThat(event.shipmentId()).isEqualTo(15);
        assertThat(event.latitude()).isEqualTo(50.455);
        assertThat(event.longitude()).isEqualTo(30.53);
        assertThat(event.recordedAt()).isEqualTo(
                LocalDateTime.of(2026, 11, 12, 13, 26, 40)
        );

        String json = new JsonMapper().writeValueAsString(event);

        assertThat(json).isEqualTo(
                "{\"locationId\":11,\"shipmentId\":15,"
                        + "\"latitude\":50.455,\"longitude\":30.53,"
                        + "\"recordedAt\":\"2026-11-12T13:26:40\"}"
        );
    }

    @Test
    void rejectsAnotherShipmentAndInvalidCoordinates() {
        ShipmentLocationUpdate anotherShipment =
                ShipmentLocationUpdate.newBuilder()
                        .setLocationId(11)
                        .setShipmentId(99)
                        .setLatitude(50.455)
                        .setLongitude(30.53)
                        .build();
        ShipmentLocationUpdate invalidCoordinates =
                ShipmentLocationUpdate.newBuilder()
                        .setLocationId(12)
                        .setShipmentId(15)
                        .setLatitude(91)
                        .setLongitude(30.53)
                        .build();

        assertThatThrownBy(() -> mapper.toEvent(15, anotherShipment))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mapper.toEvent(15, invalidCoordinates))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
