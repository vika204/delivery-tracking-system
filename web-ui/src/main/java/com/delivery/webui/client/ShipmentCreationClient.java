package com.delivery.webui.client;

import com.delivery.webui.client.dto.CreateShipmentRequest;
import com.delivery.webui.client.dto.CreatedShipmentResponse;
import com.delivery.webui.client.dto.ShipmentQuoteRequest;
import com.delivery.webui.client.dto.ShipmentQuoteResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/** Pricing і CreateShipment у shipment-service від імені відправника (X-User-Id). */
@Component
public class ShipmentCreationClient {

    static final String USER_ID = "X-User-Id";
    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String REPLAYED = "Idempotent-Replayed";

    private final RestClient restClient;

    @Autowired
    public ShipmentCreationClient(RestClient.Builder builder, @Value("${shipment.client.base-url}") String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    public ShipmentCreationClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public ShipmentQuoteResponse quote(long senderId, ShipmentQuoteRequest request) {
        return restClient.post().uri("/shipments/quotes")
                .header(USER_ID, String.valueOf(senderId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve().body(ShipmentQuoteResponse.class);
    }

    /** Idempotency-Key передається як є: повтор з тим самим ключем поверне вже створену доставку. */
    public Creation create(long senderId, String idempotencyKey, CreateShipmentRequest request) {
        ResponseEntity<CreatedShipmentResponse> response = restClient.post().uri("/shipments")
                .header(USER_ID, String.valueOf(senderId))
                .header(IDEMPOTENCY_KEY, idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve().toEntity(CreatedShipmentResponse.class);
        return new Creation(response.getBody(), "true".equals(response.getHeaders().getFirst(REPLAYED)));
    }

    public record Creation(CreatedShipmentResponse shipment, boolean replayed) {
    }
}
