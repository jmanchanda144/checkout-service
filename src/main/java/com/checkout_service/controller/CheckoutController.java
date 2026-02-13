package com.checkout_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.checkout_service.dto.CreateCheckoutRequest;
import com.checkout_service.service.CheckoutService;

@RestController
@RequestMapping("/checkout")
public class CheckoutController {

    private final CheckoutService service;

    public CheckoutController(CheckoutService service) {
        this.service = service;
    }

@PostMapping
public ResponseEntity<Long> create(
        @RequestBody CreateCheckoutRequest request) {
    return ResponseEntity.ok(
            service.createAndInitiate(request));
}
}
