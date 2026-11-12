package com.delivery.webui.client;

import com.delivery.webui.client.dto.DispatchCourierResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class DispatchCourierClient {

    private final RestClient restClient;
    private final boolean enabled;

    @Autowired
    public DispatchCourierClient(
            RestClient.Builder builder,
            @Value("${dispatch.client.base-url}") String baseUrl,
            @Value("${dispatch.courier-lookup.enabled:false}") boolean enabled
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
        this.enabled = enabled;
    }

    DispatchCourierClient(RestClient restClient, boolean enabled) {
        this.restClient = restClient;
        this.enabled = enabled;
    }

    public DispatchCourierResponse getShipmentCourier(long shipmentId) {
        if (!enabled) {
            return null;
        }

        return restClient.get()
                .uri(
                        "/dispatch/shipments/{shipmentId}/courier",
                        shipmentId
                )
                .retrieve()
                .body(DispatchCourierResponse.class);
    }
}
