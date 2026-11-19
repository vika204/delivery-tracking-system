package com.delivery.webui.client;

import com.delivery.webui.client.dto.RecordLocationRequest;
import com.delivery.webui.client.dto.TrackingLocationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

@Component
public class TrackingLocationClient {

    private static final ParameterizedTypeReference<List<TrackingLocationResponse>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;

    @Autowired
    public TrackingLocationClient(
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

    public TrackingLocationClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public List<TrackingLocationResponse> getShipmentLocations(
            long shipmentId,
            long afterLocationId,
            int limit
    ) {
        List<TrackingLocationResponse> locations = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/tracking/shipments/{shipmentId}/locations")
                        .queryParam("afterLocationId", afterLocationId)
                        .queryParam("limit", limit)
                        .build(shipmentId))
                .retrieve()
                .body(RESPONSE_TYPE);

        return locations == null ? List.of() : locations;
    }

    /**
     * Запис GPS-точки; tracking-service перевіряє координати й публікує точку підписникам стріму.
     * 200 замість 201 означає, що точку з цим pointId уже було збережено.
     */
    public RecordedLocation recordLocation(long shipmentId, RecordLocationRequest request) {
        ResponseEntity<TrackingLocationResponse> response = restClient.post()
                .uri("/tracking/shipments/{shipmentId}/locations", shipmentId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(TrackingLocationResponse.class);
        return new RecordedLocation(response.getBody(), response.getStatusCode().value() == 200);
    }

    public record RecordedLocation(TrackingLocationResponse location, boolean replayed) {
    }
}
