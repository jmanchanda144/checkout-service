package com.checkout_service.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.checkout_service.domain.CheckoutAddress;

public interface CheckoutAddressRepository 
        extends JpaRepository<CheckoutAddress, Long> {
}

