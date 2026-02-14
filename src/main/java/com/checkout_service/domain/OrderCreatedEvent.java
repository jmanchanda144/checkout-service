package com.checkout_service.domain;

public record OrderCreatedEvent(
        Long orderId,
        Long userId
) {}

