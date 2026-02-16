package com.checkout_service.domain;

public record InventoryFailedEvent(
        String eventType,
        Long orderId,
        String reason
) {}

