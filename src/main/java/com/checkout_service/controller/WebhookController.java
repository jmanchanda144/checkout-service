package com.checkout_service.controller;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.checkout_service.repo.CheckoutOrderRepository;
import com.checkout_service.service.CheckoutService;
import com.razorpay.Utils;
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/payment")
public class WebhookController {

    private final CheckoutService checkoutService;
    private final CheckoutOrderRepository orderRepo;

    @Value("${razorpay.webhook.secret}")
    private String webhookSecret;

    public WebhookController(CheckoutService checkoutService,
                             CheckoutOrderRepository orderRepo) {
        this.checkoutService = checkoutService;
        this.orderRepo = orderRepo;
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("X-Razorpay-Signature") String signature) {

        try {

            boolean isValid =
                Utils.verifyWebhookSignature(payload, signature, webhookSecret);

            if (!isValid) {
                return ResponseEntity.status(400).body("Invalid Signature");
            }

            JSONObject json = new JSONObject(payload);
            String event = json.getString("event");

            if ("order.paid".equals(event)) {

                JSONObject orderEntity = json.getJSONObject("payload")
                                              .getJSONObject("order")
                                              .getJSONObject("entity");

                JSONObject paymentEntity = json.getJSONObject("payload")
                                                .getJSONObject("payment")
                                                .getJSONObject("entity");

                String receipt = orderEntity.getString("receipt");
                String paymentId = paymentEntity.getString("id");

                Long checkoutId = Long.valueOf(receipt);
                checkoutService.handlePaymentSuccess(checkoutId, paymentId);
            }

            return ResponseEntity.ok("Handled");

        } catch (Exception e) {
            return ResponseEntity.status(400).body("Webhook Error");
        }
    }
}