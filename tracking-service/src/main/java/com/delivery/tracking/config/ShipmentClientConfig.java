package com.delivery.tracking.config;

import com.delivery.tracking.client.ShipmentClient;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

@Configuration
public class ShipmentClientConfig {

    @Bean
    public ShipmentClient shipmentClient(
            RestClient.Builder builder,
            @Value("${shipment.client.base-url}") String shipmentBaseUrl,
            ClientHttpRequestInterceptor correlationIdInterceptor) {

        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);

        requestFactory.setReadTimeout(Duration.ofSeconds(3));

        RestClient restClient = builder
                .baseUrl(shipmentBaseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor(correlationIdInterceptor)
                .build();

        HttpServiceProxyFactory factory =
                HttpServiceProxyFactory
                        .builderFor(RestClientAdapter.create(restClient))
                        .build();

        return factory.createClient(ShipmentClient.class);
    }

    @Bean
    public ClientHttpRequestInterceptor correlationIdInterceptor() {
        return (request, body, execution) -> {
            String correlationId = MDC.get("correlationId");

            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }

            request.getHeaders().add("X-Correlation-Id", correlationId);

            return execution.execute(request, body);
        };
    }
}