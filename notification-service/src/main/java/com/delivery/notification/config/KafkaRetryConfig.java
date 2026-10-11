package com.delivery.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaRetryTopic;
import org.springframework.kafka.support.converter.ByteArrayJacksonJsonMessageConverter;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableKafkaRetryTopic
public class KafkaRetryConfig {

    @Bean
    public RecordMessageConverter recordMessageConverter() {
        return new ByteArrayJacksonJsonMessageConverter();
    }

    // потрібен retry-топікам, щоб відновлювати споживання після паузи на backoff
    @Bean
    public TaskScheduler kafkaRetryTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("kafka-retry-");
        return scheduler;
    }
}
