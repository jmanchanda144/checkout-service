package com.checkout_service.dto;

import java.util.List;

public record BulkReserveRequest(
        Long orderId,
        List<Item> items
) {
    public record Item(
            Long productId,
            Integer quantity
    ) {}
}
