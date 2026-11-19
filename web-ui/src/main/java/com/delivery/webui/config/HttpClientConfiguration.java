package com.delivery.webui.config;

import com.delivery.webui.client.CourierShipmentClient;
import com.delivery.webui.client.DispatchCourierClient;
import com.delivery.webui.client.DispatchCourierDashboardClient;
import com.delivery.webui.client.ShipmentCreationClient;
import com.delivery.webui.client.ShipmentReadClient;
import com.delivery.webui.client.TrackingLocationClient;
import com.delivery.webui.client.TrackingShipmentClient;
import com.delivery.webui.client.NotificationClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

@Configuration
@ImportHttpServices(group = "shipment", types = {ShipmentReadClient.class, CourierShipmentClient.class})
@ImportHttpServices(group = "shipment-creation", types = ShipmentCreationClient.class)
@ImportHttpServices(group = "dispatch", types = {DispatchCourierClient.class, DispatchCourierDashboardClient.class})
@ImportHttpServices(group = "tracking", types = {TrackingShipmentClient.class, TrackingLocationClient.class})
@ImportHttpServices(group = "notification", types = NotificationClient.class)
public class HttpClientConfiguration {
}
