package com.delivery.webui.config;

import com.delivery.tracking.grpc.ShipmentTrackingServiceGrpc;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.ImportGrpcClients;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "tracking.stream",
        name = "enabled",
        havingValue = "true"
)
@ImportGrpcClients(
        target = "tracking",
        types = ShipmentTrackingServiceGrpc.ShipmentTrackingServiceStub.class
)
public class TrackingGrpcClientConfiguration {

    @Bean
    public ThreadPoolTaskScheduler trackingStreamTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("tracking-sse-heartbeat-");
        scheduler.setDaemon(true);
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
