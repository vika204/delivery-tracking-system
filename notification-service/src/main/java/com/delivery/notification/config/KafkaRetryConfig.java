package com.delivery.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.converter.ByteArrayJacksonJsonMessageConverter;
import org.springframework.kafka.support.converter.RecordMessageConverter;

@Configuration
public class KafkaRetryConfig {

    @Bean
    public RecordMessageConverter recordMessageConverter() {
        return new ByteArrayJacksonJsonMessageConverter();
    }
}
