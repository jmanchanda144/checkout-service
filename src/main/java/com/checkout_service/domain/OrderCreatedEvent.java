package com.checkout_service.domain;

import java.util.List;

public record OrderCreatedEvent(
    Long orderId,
    Long userId,
    List<Item> items
) {
    public record Item(
        Long productId,
        Integer quantity
    ) {}
}

