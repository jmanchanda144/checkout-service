package com.checkout_service.component;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.checkout_service.service.CheckoutOutboxService;
import com.checkout_service.service.OutboxLockingService;

@Component
public class CheckoutOutboxScheduler {

    private final CheckoutOutboxService service;
    private final OutboxLockingService lockingService;

    public CheckoutOutboxScheduler(
            CheckoutOutboxService service,
            OutboxLockingService lockingService) {
        this.service = service;
        this.lockingService = lockingService;
    }

    @Scheduled(fixedDelay = 5000)
    public void publishEvents() {
        service.publishEvents();
    }

    @Scheduled(fixedDelay = 60000)
    public void recover() {
        lockingService.recoverStuckEvents();
    }
}