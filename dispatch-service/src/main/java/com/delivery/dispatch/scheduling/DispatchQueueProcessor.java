package com.delivery.dispatch.scheduling;

import com.delivery.dispatch.config.DispatchProperties;
import com.delivery.dispatch.entity.DispatchRequestStatus;
import com.delivery.dispatch.entity.OfferStatus;
import com.delivery.dispatch.repository.DeliveryOfferRepository;
import com.delivery.dispatch.repository.DispatchRequestRepository;
import com.delivery.dispatch.service.CourierCapacityFreedEvent;
import com.delivery.dispatch.service.DispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Черга доставок без кур'єра та протерміновані пропозиції.
 * Кожна доставка обробляється в окремій транзакції, тож збій однієї не зупиняє решту.
 */
@Component
public class DispatchQueueProcessor {

    private static final Logger log = LoggerFactory.getLogger(DispatchQueueProcessor.class);
    private static final List<DispatchRequestStatus> QUEUE = List.of(
            DispatchRequestStatus.SEARCHING, DispatchRequestStatus.WAITING_FOR_COURIERS);

    private final DispatchService dispatchService;
    private final DispatchRequestRepository requestRepository;
    private final DeliveryOfferRepository offerRepository;
    private final DispatchProperties properties;
    private final Clock clock;

    public DispatchQueueProcessor(DispatchService dispatchService, DispatchRequestRepository requestRepository,
                                  DeliveryOfferRepository offerRepository, DispatchProperties properties, Clock clock) {
        this.dispatchService = dispatchService;
        this.requestRepository = requestRepository;
        this.offerRepository = offerRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /** Закриває пропозиції без відповіді (CourierProposalExpired) і передає доставку наступному кандидату. */
    public void expireOffers() {
        for (Long offerId : offerRepository.findIdsByStatusAndExpiresAtBefore(OfferStatus.PENDING, LocalDateTime.now(clock))) {
            try {
                dispatchService.expireOffer(offerId);
            } catch (RuntimeException ex) {
                log.error("Failed to expire offer {}", offerId, ex);
            }
        }
    }

    /** Кур'єр звільнився або вийшов на лінію: одразу пробуємо роздати чергу. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCapacityFreed(CourierCapacityFreedEvent event) {
        processQueue();
    }

    /** Повторний пошук для доставок без кур'єра (US-3.1: «доставка залишається створеною й очікує»). */
    public void processQueue() {
        List<Long> queue = requestRepository.findQueue(QUEUE, PageRequest.of(0, properties.queueBatchSize()));
        for (Long shipmentId : queue) {
            try {
                dispatchService.dispatch(shipmentId);
            } catch (RuntimeException ex) {
                log.error("Failed to dispatch shipment {}", shipmentId, ex);
            }
        }
    }
}
