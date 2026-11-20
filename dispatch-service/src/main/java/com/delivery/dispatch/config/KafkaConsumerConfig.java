package com.delivery.dispatch.config;

import com.delivery.dispatch.kafka.InvalidShipmentEventException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/** Повтори з експоненційною затримкою; зіпсовані події й вичерпані спроби — у "*.dispatch-dlt". */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (failed, ex) -> new TopicPartition(failed.topic() + KafkaTopicConfig.DLT_SUFFIX, -1)
        );

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(4);
        backOff.setInitialInterval(1_000);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10_000);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(InvalidShipmentEventException.class);
        return handler;
    }
}
