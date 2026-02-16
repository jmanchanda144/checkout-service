package com.checkout_service.dto;

public record CheckoutStatusResponse(
        String id,
        String status,
        String razorpayOrderId,
        String razorpayPaymentId,
        String totalAmount
) {}
