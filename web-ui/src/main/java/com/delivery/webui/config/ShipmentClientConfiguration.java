package com.delivery.webui.config;

import com.delivery.webui.client.ShipmentReadClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class ShipmentClientConfiguration {

    @Bean
    public ShipmentReadClient shipmentReadClient(
            RestClient.Builder builder,
            @Value("${shipment.client.base-url}") String baseUrl
    ) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        RestClient restClient = builder.baseUrl(baseUrl).requestFactory(requestFactory).build();

        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build().createClient(ShipmentReadClient.class);
    }
}
