package com.checkout_service.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.checkout_service.dto.CheckoutInitResponse;
import com.checkout_service.dto.CreateCheckoutRequest;
import com.checkout_service.service.CheckoutService;
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/checkout")
public class CheckoutController {

    private final CheckoutService service;

    public CheckoutController(CheckoutService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<?> create(
            @RequestBody CreateCheckoutRequest request) {

        try {

            CheckoutInitResponse response =
                    service.createAndInitiate(request);

            return ResponseEntity.ok(response);

        } catch (Exception ex) {

            ex.printStackTrace(); // VERY IMPORTANT

            return ResponseEntity
                    .status(500)
                    .body(Map.of(
                            "error", "CHECKOUT_FAILED",
                            "message", ex.getMessage()
                    ));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getStatus(@PathVariable Long id) {
        return ResponseEntity.ok(service.getOrder(id));
    }
}
