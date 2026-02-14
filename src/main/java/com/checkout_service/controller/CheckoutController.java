package com.checkout_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.checkout_service.dto.CreateCheckoutRequest;
import com.checkout_service.service.CheckoutService;
import com.razorpay.RazorpayException;
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/checkout")
public class CheckoutController {

    private final CheckoutService service;

    public CheckoutController(CheckoutService service) {
        this.service = service;
    }

@PostMapping
public ResponseEntity<String> create(
        @RequestBody CreateCheckoutRequest request) {
            System.out.println(request);
        try {
            return ResponseEntity.ok(
                    service.createAndInitiate(request));
        } catch (RazorpayException ex) {
            System.getLogger(CheckoutController.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
        return null;
}
}
