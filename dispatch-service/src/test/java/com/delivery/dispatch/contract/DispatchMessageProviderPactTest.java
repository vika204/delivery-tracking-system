package com.delivery.dispatch.contract;

import au.com.dius.pact.provider.PactVerifyProvider;
import au.com.dius.pact.provider.junit5.MessageTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import com.delivery.dispatch.entity.Assignment;
import com.delivery.dispatch.entity.AssignmentStatus;
import com.delivery.dispatch.entity.Courier;
import com.delivery.dispatch.entity.DispatchRequest;
import com.delivery.dispatch.outbox.DispatchEventOutbox;
import com.delivery.dispatch.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;

/**
 * Verifies the Kafka message pact of the consumer of dispatch-service (shipment-service). The messages are produced
 * by the real {@link DispatchEventOutbox}.
 */
@Provider("dispatch-service")
@PactFolder("../pacts/messages")
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=localhost:9999",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false",
        "outbox.relay.enabled=false",
        "dispatch.scheduling.enabled=false"
})
@Testcontainers
class DispatchMessageProviderPactTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private DispatchEventOutbox eventOutbox;
    @Autowired
    private OutboxEventRepository outboxRepository;

    @BeforeEach
    void setUp(PactVerificationContext context) {
        context.setTarget(new MessageTestTarget());
        outboxRepository.deleteAll();
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifyPact(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @PactVerifyProvider("a COURIER_ASSIGNED event")
    String courierAssigned() {
        eventOutbox.courierAssigned(assignment());
        return onlyPayload();
    }

    @PactVerifyProvider("a COURIER_UNASSIGNED event")
    String courierUnassigned() {
        eventOutbox.courierUnassigned(assignment(), "COURIER_WITHDREW");
        return onlyPayload();
    }

    @PactVerifyProvider("a COURIER_SEARCH_DELAYED event")
    String courierSearchDelayed() {
        DispatchRequest request = DispatchRequest.builder()
                .shipmentId(15L)
                .senderId(42L)
                .createdAt(Instant.now())
                .build();
        eventOutbox.searchDelayed(request, "NO_COURIER_AVAILABLE");
        return onlyPayload();
    }

    private String onlyPayload() {
        return outboxRepository.findAll().getFirst().getPayload();
    }

    private static Assignment assignment() {
        Courier courier = Courier.builder().userId(10L).displayName("Andrii Kovalenko").build();
        return Assignment.builder()
                .assignmentId(3L)
                .shipmentId(15L)
                .courier(courier)
                .status(AssignmentStatus.ACTIVE)
                .build();
    }
}
