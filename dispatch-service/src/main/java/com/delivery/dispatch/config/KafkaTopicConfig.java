package com.delivery.dispatch.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String DISPATCH_EVENTS_TOPIC = "dispatch.events";

    public static final String SHIPMENT_EVENTS_TOPIC = "shipment.events";
    public static final String SHIPMENT_STATUS_CHANGED_TOPIC = "shipment.status.changed";

    public static final String DLT_SUFFIX = ".dispatch-dlt";

    @Bean
    public NewTopic dispatchEventsTopic() {
        return topic(DISPATCH_EVENTS_TOPIC);
    }

    @Bean
    public NewTopic shipmentEventsDispatchDlt() {
        return topic(SHIPMENT_EVENTS_TOPIC + DLT_SUFFIX);
    }

    @Bean
    public NewTopic shipmentStatusChangedDispatchDlt() {
        return topic(SHIPMENT_STATUS_CHANGED_TOPIC + DLT_SUFFIX);
    }

    private static NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }
}
