package com.delivery.tracking.grpcclient;

import com.delivery.tracking.grpc.ShipmentTrackingServiceGrpc;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.grpc.client.autoconfigure.GrpcClientDefaultServiceConfigCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.grpc.client.GlobalClientInterceptor;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@AutoConfiguration
@EnableConfigurationProperties(TrackingGrpcProperties.class)
public class TrackingGrpcClientAutoConfiguration {

    @Bean
    @GlobalClientInterceptor
    @ConditionalOnMissingBean
    GrpcAttemptLoggingInterceptor grpcAttemptLoggingInterceptor() {
        return new GrpcAttemptLoggingInterceptor();
    }

    @Bean
    GrpcClientDefaultServiceConfigCustomizer trackingRetryServiceConfig(TrackingGrpcProperties properties) {
        return (channel, serviceConfig) -> {
            if (TrackingGrpcProperties.CHANNEL.equals(channel)) {
                serviceConfig.putAll(retryServiceConfig(properties.retry()));
            }
        };
    }

    private static Map<String, Object> retryServiceConfig(TrackingGrpcProperties.Retry retry) {
        Map<String, Object> retryPolicy = Map.of(
                "maxAttempts", (double) retry.maxAttempts(),
                "initialBackoff", seconds(retry.initialBackoff()),
                "maxBackoff", seconds(retry.maxBackoff()),
                "backoffMultiplier", retry.backoffMultiplier(),
                "retryableStatusCodes", List.of("UNAVAILABLE"));
        return Map.of(
                "methodConfig", List.of(Map.of(
                        "name", List.of(Map.of("service", ShipmentTrackingServiceGrpc.SERVICE_NAME)),
                        "retryPolicy", retryPolicy)),
                "retryThrottling", Map.of("maxTokens", 100.0, "tokenRatio", 0.1));
    }

    private static String seconds(Duration duration) {
        return String.format(Locale.ROOT, "%.3fs", duration.toMillis() / 1000.0);
    }
}
