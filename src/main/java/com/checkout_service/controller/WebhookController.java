package com.checkout_service.controller;

import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.checkout_service.service.PaymentService;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/payment")
public class WebhookController {

    @Value("${razorpay.webhook.secret}")
    private String webhookSecret;

    @Autowired
    private PaymentService paymentService; // Delegate to service

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload, 
            @RequestHeader("X-Razorpay-Signature") String signature) {

        try {
            // 1. Signature Verification (The "Real Thing")
            boolean isValid = Utils.verifyWebhookSignature(payload, signature, webhookSecret);
            
            if (isValid) {
                JSONObject json = new JSONObject(payload);
                String event = json.getString("event");

                if ("order.paid".equals(event)) {
                    String internalId = json.getJSONObject("payload")
                                            .getJSONObject("order")
                                            .getJSONObject("entity")
                                            .getString("receipt");
                    System.out.println("json->"+json.toString());
                    // 2. Call the service to handle the "business" part
                    paymentService.processSuccessfulPayment(internalId);
                }
                return ResponseEntity.ok("Handled");
            }
        } catch (RazorpayException | JSONException e) {
            return ResponseEntity.status(400).body("Error verifying webhook");
        }
        return ResponseEntity.status(400).body("Invalid Signature");
    }
    @PostMapping("/create-order")
    public ResponseEntity<String> createOrder(@RequestBody Map<String, Object> data) {
        try {
            // 1. Get amount from frontend (e.g., 500)
            long amount = Long.parseLong(data.get("amount").toString());
            
            // 2. Generate your Snowflake ID for the receipt
            String myReceiptId = "SNOW_" + System.currentTimeMillis(); 

            // 3. Call Service to get real Razorpay Order ID
            String razorpayOrderId = paymentService.createTransaction(amount, myReceiptId);
            System.out.println("razorpayOrderId->"+razorpayOrderId);
            // 4. Return the order_abc123 ID to the frontend
            return ResponseEntity.ok(razorpayOrderId);
            
        } catch (RazorpayException | NumberFormatException e) {
            return ResponseEntity.status(500).body("Error creating order: " + e.getMessage());
        }
    }
}