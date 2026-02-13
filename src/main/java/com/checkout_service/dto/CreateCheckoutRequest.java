package com.checkout_service.dto;

import java.util.List;

import com.checkout_service.domain.PaymentMethod;

public record CreateCheckoutRequest(
        Long userId,
        PaymentMethod paymentMethod,
        List<Item> items,
        Address address
) {

    public record Item(
            Long productId,
            Integer quantity
    ) {}

    public record Address(
            String fullName,
            String phone,
            String addressLine1,
            String city,
            String state,
            String pincode
    ) {}
}


