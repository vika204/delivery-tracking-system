package com.delivery.shipment.outbox;

import com.delivery.shipment.repository.OutboxEventRepository;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboxRelayMetricsTest {

    @Test
    @SuppressWarnings("unchecked")
    void batchIsVisibleAsActiveTaskWhileRunningAndGoneAfterwards() {
        MeterRegistry registry = new SimpleMeterRegistry();
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        List<Integer> activeDuringRun = new ArrayList<>();
        LongTaskTimer timer = LongTaskTimer.builder("delivery.outbox.relay.batch").register(registry);
        when(repository.findTop50ByPublishedFalseOrderByCreatedAtAsc()).thenAnswer(invocation -> {
            activeDuringRun.add(timer.activeTasks());
            return List.of();
        });
        OutboxRelay relay = new OutboxRelay(repository, kafka, registry);

        relay.publishPendingEvents();

        assertThat(activeDuringRun).containsExactly(1);
        assertThat(timer.activeTasks()).isZero();
    }
}
