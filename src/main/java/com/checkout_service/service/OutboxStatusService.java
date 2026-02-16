package com.checkout_service.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.checkout_service.domain.CheckoutOutboxEvent;
import com.checkout_service.domain.OutboxStatus;
import com.checkout_service.repo.CheckoutOutboxRepository;

@Service
public class OutboxStatusService {

    private final CheckoutOutboxRepository repo;

    public OutboxStatusService(CheckoutOutboxRepository repo) {
        this.repo = repo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSuccess(Long id) {
        CheckoutOutboxEvent event = repo.findById(id).orElseThrow();
        event.setStatus(OutboxStatus.SENT);
        event.setProcessedAt(Instant.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailure(Long id) {
        CheckoutOutboxEvent event = repo.findById(id).orElseThrow();
        event.setRetryCount(event.getRetryCount() + 1);

        if (event.getRetryCount() >= 10) {
            event.setStatus(OutboxStatus.DEAD);
        } else {
            event.setStatus(OutboxStatus.FAILED);
        }
    }
}

