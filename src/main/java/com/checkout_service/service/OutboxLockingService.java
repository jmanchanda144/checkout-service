package com.checkout_service.service;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.checkout_service.domain.CheckoutOutboxEvent;
import com.checkout_service.domain.OutboxStatus;
import com.checkout_service.repo.CheckoutOutboxRepository;

@Service
public class OutboxLockingService {

    private final CheckoutOutboxRepository repo;

    public OutboxLockingService(CheckoutOutboxRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public List<CheckoutOutboxEvent> lockAndMark() {

        List<CheckoutOutboxEvent> events =
                repo.findBatchForUpdate(
                        List.of(OutboxStatus.PENDING, OutboxStatus.FAILED),
                        PageRequest.of(0, 100)
                );

        for (CheckoutOutboxEvent e : events) {
            e.setStatus(OutboxStatus.PROCESSING);
        }

        return events;
    }
    @Transactional
    public void recoverStuckEvents() {

        List<CheckoutOutboxEvent> stuck =
                repo.findByStatusAndProcessedAtIsNullAndCreatedAtBefore(
                        OutboxStatus.PROCESSING,
                        Instant.now().minusSeconds(300)
                );

        for (CheckoutOutboxEvent e : stuck) {
            e.setStatus(OutboxStatus.FAILED);
        }
    }
}

