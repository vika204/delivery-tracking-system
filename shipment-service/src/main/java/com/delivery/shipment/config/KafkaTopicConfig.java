package com.delivery.shipment.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String SHIPMENT_EVENTS_TOPIC = "shipment.events";
    public static final String SHIPMENT_STATUS_CHANGED_TOPIC = "shipment.status.changed";

    @Bean
    public NewTopic shipmentEventsTopic() {
        return TopicBuilder.name(SHIPMENT_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic shipmentStatusChangedTopic() {
        return TopicBuilder.name(SHIPMENT_STATUS_CHANGED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}