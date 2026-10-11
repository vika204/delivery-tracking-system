package com.delivery.notification.kafka;

import com.delivery.events.ShipmentCreatedEvent;
import com.delivery.notification.config.KafkaRetryConfig;
import com.delivery.notification.handler.ShipmentEventHandler;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// Контекст містить лише слухача й Kafka: обробник підмінено моком, база даних не потрібна.
@SpringBootTest(
        classes = {ShipmentEventListener.class, KafkaRetryConfig.class},
        properties = {
                "app.kafka.retry.attempts=4",
                "app.kafka.retry.delay-ms=100",
                "app.kafka.retry.multiplier=2.0",
                "app.kafka.retry.max-delay-ms=400"
        }
)
@ImportAutoConfiguration(KafkaAutoConfiguration.class)
@EmbeddedKafka(
        partitions = 1,
        topics = ShipmentEventListener.TOPIC,
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@DirtiesContext
class ShipmentEventListenerDltIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String FIRST_RETRY_TOPIC = ShipmentEventListener.TOPIC + "-retry-0";

    @Autowired
    private KafkaTemplate<String, byte[]> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @MockitoBean
    private ShipmentEventHandler handler;

    private Consumer<String, String> dltConsumer;

    @BeforeEach
    void subscribeToDlt() {
        dltConsumer = consumerFor(ShipmentEventListener.DLT_TOPIC);
    }

    @AfterEach
    void closeConsumer() {
        dltConsumer.close();
    }

    @Test
    void shouldMoveEventToDltAfterRetriesAreExhausted() {
        // given
        String key = "exhausted-" + UUID.randomUUID();
        String json = eventJson(UUID.randomUUID());
        doThrow(new IllegalStateException("Notification database is unavailable"))
                .when(handler)
                .handle(any(ShipmentCreatedEvent.class));

        // when
        send(key, json);

        // then
        ConsumerRecord<String, String> dltRecord = awaitDltRecord(key);
        assertThat(dltRecord.value()).isEqualTo(json);
        assertThat(header(dltRecord, KafkaHeaders.ORIGINAL_TOPIC)).isEqualTo(ShipmentEventListener.TOPIC);
        assertThat(header(dltRecord, KafkaHeaders.EXCEPTION_MESSAGE))
                .contains("Notification database is unavailable");
        assertThat(wasRetried(key)).isTrue();
        verify(handler, times(4)).handle(any(ShipmentCreatedEvent.class));
    }

    @Test
    void shouldMoveMalformedJsonToDltWithoutRetries() {
        // given
        String key = "malformed-" + UUID.randomUUID();
        String json = "{not-a-json";

        // when
        send(key, json);

        // then
        ConsumerRecord<String, String> dltRecord = awaitDltRecord(key);
        assertThat(dltRecord.value()).isEqualTo(json);
        assertThat(wasRetried(key)).isFalse();
        verify(handler, never()).handle(any(ShipmentCreatedEvent.class));
    }

    @Test
    void shouldMoveEventViolatingContractToDltWithoutRetries() {
        // given
        String key = "invalid-" + UUID.randomUUID();
        String json = """
                {"eventType":"SHIPMENT_CREATED","payload":{"shipmentId":1,"userId":42,"status":"WAITING_FOR_COURIER"}}
                """.strip();

        // when
        send(key, json);

        // then
        ConsumerRecord<String, String> dltRecord = awaitDltRecord(key);
        assertThat(header(dltRecord, KafkaHeaders.EXCEPTION_CAUSE_FQCN))
                .isEqualTo(InvalidShipmentEventException.class.getName());
        assertThat(wasRetried(key)).isFalse();
        verify(handler, never()).handle(any(ShipmentCreatedEvent.class));
    }

    @Test
    void shouldNotUseDltWhenRetrySucceeds() {
        // given
        String key = "recovered-" + UUID.randomUUID();
        doThrow(new IllegalStateException("Temporary failure"))
                .doThrow(new IllegalStateException("Temporary failure"))
                .doNothing()
                .when(handler)
                .handle(any(ShipmentCreatedEvent.class));

        // when
        send(key, eventJson(UUID.randomUUID()));

        // then
        verify(handler, timeout(TIMEOUT.toMillis()).times(3)).handle(any(ShipmentCreatedEvent.class));
        assertThat(pollDltRecord(key)).isEmpty();
    }

    private void send(String key, String json) {
        kafkaTemplate.send(ShipmentEventListener.TOPIC, key, json.getBytes(StandardCharsets.UTF_8)).join();
    }

    private ConsumerRecord<String, String> awaitDltRecord(String key) {
        return await().atMost(TIMEOUT).until(() -> pollDltRecord(key), Optional::isPresent).orElseThrow();
    }

    private Optional<ConsumerRecord<String, String>> pollDltRecord(String key) {
        for (ConsumerRecord<String, String> record : dltConsumer.poll(Duration.ofSeconds(2))) {
            if (key.equals(record.key())) {
                return Optional.of(record);
            }
        }
        return Optional.empty();
    }

    // будь-яка повторна спроба спершу проходить через перший retry-топік
    private boolean wasRetried(String key) {
        try (Consumer<String, String> retryConsumer = consumerFor(FIRST_RETRY_TOPIC)) {
            for (ConsumerRecord<String, String> record : retryConsumer.poll(Duration.ofSeconds(3))) {
                if (key.equals(record.key())) {
                    return true;
                }
            }
            return false;
        }
    }

    private Consumer<String, String> consumerFor(String topic) {
        Consumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        ));
        consumer.subscribe(List.of(topic));
        return consumer;
    }

    private String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private String eventJson(UUID eventId) {
        return """
                {"eventId":"%s","eventType":"SHIPMENT_CREATED","occurredAt":"2026-10-11T10:15:30Z",\
                "payload":{"shipmentId":1,"userId":42,"status":"WAITING_FOR_COURIER"}}
                """.formatted(eventId).strip();
    }
}
