package com.delivery.shipment.contract;

import com.delivery.shipment.exception.InvalidDispatchEventException;
import com.delivery.shipment.kafka.DispatchEventListener;
import com.delivery.shipment.service.ShipmentLifecycleService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Negative side of the dispatch -> shipment event contract: messages that break it must be rejected, not applied. */
class DispatchEventValidationTest {

    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private final ShipmentLifecycleService lifecycle = mock(ShipmentLifecycleService.class);
    private final DispatchEventListener listener = new DispatchEventListener(mapper, lifecycle);

    @Test
    void anAssignmentWithoutACourierIsRejected() {
        String withoutCourier = """
                {"eventId":"7d444840-9dc0-11d1-b245-5ffdce74fad2","eventType":"COURIER_ASSIGNED",
                 "occurredAt":"2026-11-22T12:00:00Z","shipmentId":15}
                """;

        assertThatThrownBy(() -> listener.onDispatchEvent(withoutCourier)).isInstanceOf(InvalidDispatchEventException.class);
        verify(lifecycle, never()).onDispatchEvent(any());
    }

    @Test
    void malformedJsonIsRejected() {
        assertThatThrownBy(() -> listener.onDispatchEvent("{not json")).isInstanceOf(InvalidDispatchEventException.class);
        verify(lifecycle, never()).onDispatchEvent(any());
    }
}
