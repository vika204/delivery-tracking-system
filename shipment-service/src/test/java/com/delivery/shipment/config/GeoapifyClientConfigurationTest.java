package com.delivery.shipment.config;

import com.delivery.shipment.geo.GeoapifyApi;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.service.HttpServiceClientProperties;
import org.springframework.boot.http.client.autoconfigure.service.HttpServiceClientPropertiesAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.service.HttpServiceClientAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.service.registry.HttpServiceProxyRegistry;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class GeoapifyClientConfigurationTest {

    @Test
    void springRegistersTheProxyWithConfiguredUrlAndTimeouts() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(HttpClientAutoConfiguration.class,
                        ImperativeHttpClientAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class,
                        RestClientAutoConfiguration.class, HttpServiceClientPropertiesAutoConfiguration.class,
                        HttpServiceClientAutoConfiguration.class))
                .withUserConfiguration(GeoapifyClientConfiguration.class)
                .withPropertyValues("geoapify.api-key=mock-geoapify-key")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(GeoapifyApi.class))
                            .isSameAs(context.getBean(HttpServiceProxyRegistry.class).getClient(GeoapifyApi.class));
                    var properties = context.getBean(HttpServiceClientProperties.class).get("geoapify");
                    assertThat(properties.getBaseUrl()).isEqualTo("https://api.geoapify.com");
                    assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(3));
                });
    }
}
