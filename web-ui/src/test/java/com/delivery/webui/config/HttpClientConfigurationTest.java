package com.delivery.webui.config;

import com.delivery.webui.client.CourierShipmentClient;
import com.delivery.webui.client.DispatchCourierClient;
import com.delivery.webui.client.DispatchCourierDashboardClient;
import com.delivery.webui.client.ShipmentCreationClient;
import com.delivery.webui.client.ClientShipmentClient;
import com.delivery.webui.client.TrackingLocationClient;
import com.delivery.webui.client.TrackingShipmentClient;
import com.delivery.webui.client.NotificationClient;
import com.delivery.webui.client.UserProfileClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.autoconfigure.service.HttpServiceClientProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.web.service.registry.HttpServiceProxyRegistry;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "shipment.client.base-url=http://shipment.test",
        "dispatch.client.base-url=http://dispatch.test",
        "tracking.client.base-url=http://tracking.test",
        "notification.client.base-url=http://notification.test",
        "auth.client.base-url=http://auth.test"
})
class HttpClientConfigurationTest {

    @Autowired
    private HttpServiceProxyRegistry registry;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private HttpServiceClientProperties properties;

    @Test
    void registersAllClientsAsInjectableHttpServiceProxies() {
        for (Class<?> type : List.of(ClientShipmentClient.class, ShipmentCreationClient.class,
                CourierShipmentClient.class, DispatchCourierClient.class, DispatchCourierDashboardClient.class,
                TrackingShipmentClient.class, TrackingLocationClient.class, NotificationClient.class, UserProfileClient.class)) {
            assertThat(context.getBean(type)).isSameAs(registry.getClient(type));
        }
    }

    @Test
    void preservesServiceUrlsAndTimeoutsIncludingLongerQuoteTimeout() {
        assertThat(properties.get("shipment").getBaseUrl()).isEqualTo("http://shipment.test");
        assertThat(properties.get("shipment-creation").getBaseUrl()).isEqualTo("http://shipment.test");
        assertThat(properties.get("dispatch").getBaseUrl()).isEqualTo("http://dispatch.test");
        assertThat(properties.get("tracking").getBaseUrl()).isEqualTo("http://tracking.test");
        assertThat(properties.get("notification").getBaseUrl()).isEqualTo("http://notification.test");
        assertThat(properties.get("auth").getBaseUrl()).isEqualTo("http://auth.test");
        for (String group : List.of("shipment", "shipment-creation", "dispatch", "tracking", "notification", "auth")) {
            assertThat(properties.get(group).getConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
            assertThat(properties.get(group).getReadTimeout())
                    .isEqualTo(Duration.ofSeconds(group.equals("shipment-creation") ? 20 : 3));
        }
    }
}
