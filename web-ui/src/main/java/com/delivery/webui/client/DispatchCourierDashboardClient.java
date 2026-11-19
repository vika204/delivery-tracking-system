package com.delivery.webui.client;

import com.delivery.webui.client.dto.DispatchCourierAssignment;
import com.delivery.webui.client.dto.DispatchCourierOffer;
import com.delivery.webui.client.dto.DispatchCourierProfile;
import com.delivery.webui.client.dto.DispatchCourierStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Courier API у dispatch-service; кожен запит іде від імені кур'єра (X-User-Id). */
@Component
public class DispatchCourierDashboardClient {

    static final String USER_ID = "X-User-Id";

    private final RestClient restClient;

    @Autowired
    public DispatchCourierDashboardClient(RestClient.Builder builder, @Value("${dispatch.client.base-url}") String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    public DispatchCourierDashboardClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public DispatchCourierProfile profile(long courierId) {
        return restClient.get().uri("/couriers/{id}", courierId)
                .retrieve().body(DispatchCourierProfile.class);
    }

    public DispatchCourierStatus status(long courierId) {
        return restClient.get().uri("/couriers/{id}/status", courierId)
                .header(USER_ID, String.valueOf(courierId))
                .retrieve().body(DispatchCourierStatus.class);
    }

    /** null — пропозиції немає (204). */
    public DispatchCourierOffer currentOffer(long courierId) {
        return restClient.get().uri("/couriers/{id}/offers/current", courierId)
                .header(USER_ID, String.valueOf(courierId))
                .retrieve().body(DispatchCourierOffer.class);
    }

    public List<DispatchCourierAssignment> activeAssignments(long courierId) {
        List<DispatchCourierAssignment> assignments = restClient.get()
                .uri("/couriers/{id}/assignments?active=true", courierId)
                .header(USER_ID, String.valueOf(courierId))
                .retrieve().body(new ParameterizedTypeReference<>() {});
        return assignments == null ? List.of() : assignments;
    }

    public void setAvailability(long courierId, boolean online) {
        restClient.put().uri("/couriers/{id}/availability", courierId)
                .header(USER_ID, String.valueOf(courierId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("online", online))
                .retrieve().toBodilessEntity();
    }

    public void acceptOffer(long courierId, long offerId) {
        restClient.post().uri("/couriers/{id}/offers/{offerId}/accept", courierId, offerId)
                .header(USER_ID, String.valueOf(courierId))
                .retrieve().toBodilessEntity();
    }

    public void declineOffer(long courierId, long offerId) {
        restClient.post().uri("/couriers/{id}/offers/{offerId}/decline", courierId, offerId)
                .header(USER_ID, String.valueOf(courierId))
                .retrieve().toBodilessEntity();
    }
}
