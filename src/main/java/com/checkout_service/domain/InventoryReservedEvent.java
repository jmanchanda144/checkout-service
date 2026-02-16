package com.checkout_service.domain;

public record InventoryReservedEvent(
        String eventType,
        Long orderId
) {}
