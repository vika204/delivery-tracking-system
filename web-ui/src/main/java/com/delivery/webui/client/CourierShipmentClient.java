package com.delivery.webui.client;

import com.delivery.webui.client.dto.CourierShipmentResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Команди кур'єра в shipment-service. Призначення перевіряє shipment-service: чужа посилка — 403,
 * недоступний перехід стану — 409.
 */
@Component
public class CourierShipmentClient {

    static final String USER_ID = "X-User-Id";

    private final RestClient restClient;

    @Autowired
    public CourierShipmentClient(RestClient.Builder builder, @Value("${shipment.client.base-url}") String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    public CourierShipmentClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public CourierShipmentResponse courierView(long courierId, long shipmentId) {
        return restClient.get().uri("/shipments/{id}/courier-view", shipmentId)
                .header(USER_ID, String.valueOf(courierId))
                .retrieve().body(CourierShipmentResponse.class);
    }

    /** ConfirmParcelPickup: ASSIGNED → IN_TRANSIT. */
    public void confirmPickup(long courierId, long shipmentId) {
        command(courierId, shipmentId, "pickup");
    }

    public void completeDelivery(long courierId, long shipmentId) {
        command(courierId, shipmentId, "deliver");
    }

    private void command(long courierId, long shipmentId, String action) {
        restClient.post().uri("/shipments/{id}/{action}", shipmentId, action)
                .header(USER_ID, String.valueOf(courierId))
                .retrieve().toBodilessEntity();
    }
}
