package com.checkout_service.service;

import java.util.List;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.checkout_service.domain.CheckoutOutboxEvent;
import com.checkout_service.repo.CheckoutOutboxRepository;

@Service
public class CheckoutOutboxService {

    private final CheckoutOutboxRepository repo;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxStatusService statusService;
    private final OutboxLockingService lockingService;

    public CheckoutOutboxService(KafkaTemplate<String, 
                                String> kafkaTemplate, 
                                CheckoutOutboxRepository repo,
                                OutboxStatusService statusService,
                                OutboxLockingService lockingService) {
        this.kafkaTemplate = kafkaTemplate;
        this.repo = repo;
        this.statusService = statusService;
        this.lockingService = lockingService;
    }

    public void publishEvents() {
        List<CheckoutOutboxEvent> events = lockingService.lockAndMark();
        events.forEach(this::publishSingle);
    }

    public void publishSingle(CheckoutOutboxEvent event) {

        kafkaTemplate.send(
                event.getTopic(),
                event.getAggregateId().toString(),
                event.getPayload()
        ).whenComplete((result, ex) -> {

            if (ex == null) {
                statusService.markSuccess(event.getId());
            } else {
                statusService.markFailure(event.getId());
            }
        });
    }
}
