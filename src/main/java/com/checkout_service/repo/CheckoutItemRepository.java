package com.checkout_service.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.checkout_service.domain.CheckoutItem;

public interface CheckoutItemRepository 
        extends JpaRepository<CheckoutItem, Long> {

    List<CheckoutItem> findByCheckoutId(Long checkoutId);
}

