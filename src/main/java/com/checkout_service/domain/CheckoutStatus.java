package com.checkout_service.domain;

public enum CheckoutStatus {
    CREATED,
    PAYMENT_PENDING,
    PAYMENT_SUCCESS,
    PAYMENT_FAILED,
    CANCELLED
}

