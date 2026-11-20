package com.delivery.shipment.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String SHIPMENT_EVENTS_TOPIC = "shipment.events";
    public static final String SHIPMENT_STATUS_CHANGED_TOPIC = "shipment.status.changed";
    public static final String SHIPMENT_DETAILS_UPDATED_TOPIC = "shipment.details.updated";

    public static final String DISPATCH_EVENTS_TOPIC = "dispatch.events";
    public static final String DISPATCH_EVENTS_DLT = DISPATCH_EVENTS_TOPIC + "-dlt";

    @Bean
    public NewTopic shipmentEventsTopic() {
        return topic(SHIPMENT_EVENTS_TOPIC);
    }

    @Bean
    public NewTopic shipmentStatusChangedTopic() {
        return topic(SHIPMENT_STATUS_CHANGED_TOPIC);
    }

    @Bean
    public NewTopic shipmentDetailsUpdatedTopic() {
        return topic(SHIPMENT_DETAILS_UPDATED_TOPIC);
    }

    @Bean
    public NewTopic dispatchEventsDltTopic() {
        return topic(DISPATCH_EVENTS_DLT);
    }

    private static NewTopic topic(String name) {
        return TopicBuilder.name(name)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
