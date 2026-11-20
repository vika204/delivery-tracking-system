package com.delivery.tracking.grpcclient;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.grpc.client.autoconfigure.GrpcClientDefaultServiceConfigCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingGrpcClientAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TrackingGrpcClientAutoConfiguration.class));

    @Test
    void appliesRetryPolicyFromPropertiesToTrackingChannelOnly() {
        contextRunner
                .withPropertyValues("tracking.grpc.retry.max-attempts=4", "tracking.grpc.retry.initial-backoff=50ms")
                .run(context -> {
                    GrpcClientDefaultServiceConfigCustomizer customizer =
                            context.getBean(GrpcClientDefaultServiceConfigCustomizer.class);

                    Map<String, Object> tracking = new HashMap<>();
                    customizer.customize(TrackingGrpcProperties.CHANNEL, tracking);
                    Map<String, Object> other = new HashMap<>();
                    customizer.customize("other", other);

                    Map<?, ?> methodConfig = (Map<?, ?>) ((List<?>) tracking.get("methodConfig")).getFirst();
                    Map<String, Object> retryPolicy = (Map<String, Object>) methodConfig.get("retryPolicy");
                    assertThat(retryPolicy).containsEntry("maxAttempts", 4.0);
                    assertThat(retryPolicy).containsEntry("initialBackoff", "0.050s");
                    assertThat(retryPolicy).containsEntry("maxBackoff", "4.000s");
                    assertThat(retryPolicy).containsEntry("retryableStatusCodes", List.of("UNAVAILABLE"));
                    assertThat(other).isEmpty();
                });
    }

    @Test
    void usesSameDefaultsForChannelRetryAndStreamReconnect() {
        contextRunner.run(context -> {
            TrackingGrpcProperties properties = context.getBean(TrackingGrpcProperties.class);

            assertThat(properties.deadline()).isEqualTo(Duration.ofSeconds(30));
            assertThat(properties.retry().maxAttempts()).isEqualTo(5);
            assertThat(properties.retry().backoff(1)).isEqualTo(Duration.ofMillis(500));
            assertThat(properties.retry().backoff(10)).isEqualTo(Duration.ofSeconds(4));
            assertThat(context).hasSingleBean(GrpcAttemptLoggingInterceptor.class);
        });
    }
}
