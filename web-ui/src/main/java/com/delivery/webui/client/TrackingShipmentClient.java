package com.delivery.webui.client;

import com.delivery.webui.client.dto.TrackingShipmentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class TrackingShipmentClient {

    private final RestClient restClient;

    public TrackingShipmentClient(
            RestClient.Builder builder,
            @Value("${tracking.client.base-url}") String baseUrl
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);

        requestFactory.setReadTimeout(Duration.ofSeconds(3));

        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public TrackingShipmentResponse getShipmentById(long shipmentId) {
        return restClient.get()
                .uri("/tracking/shipments/{id}", shipmentId)
                .retrieve()
                .body(TrackingShipmentResponse.class);
    }
}