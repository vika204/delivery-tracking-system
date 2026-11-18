package com.delivery.tracking.grpcclient;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.grpc.client.GlobalClientInterceptor;

/**
 * Підключає логування кожної спроби gRPC-виклику в сервісі, що додав залежність tracking-grpc-client.
 */
@AutoConfiguration
public class TrackingGrpcClientAutoConfiguration {

    @Bean
    @GlobalClientInterceptor
    @ConditionalOnMissingBean
    GrpcAttemptLoggingInterceptor grpcAttemptLoggingInterceptor() {
        return new GrpcAttemptLoggingInterceptor();
    }
}
