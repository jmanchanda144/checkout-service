package com.checkout_service.dto;

public record CheckoutInitResponse(
        String checkoutId,
        String razorpayOrderId,
        Long amountInPaise,
        String key
) {}
