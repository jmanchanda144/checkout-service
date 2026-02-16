package com.checkout_service.domain;

public record PaymentSuccessEvent(
        Long orderId,
        String paymentId
) {}
