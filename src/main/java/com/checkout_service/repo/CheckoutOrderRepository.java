package com.checkout_service.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.checkout_service.domain.CheckoutOrder;

public interface CheckoutOrderRepository 
        extends JpaRepository<CheckoutOrder, Long> {

    Optional<CheckoutOrder> findByRazorpayOrderId(String razorpayOrderId);
}

