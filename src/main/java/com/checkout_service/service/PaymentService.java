package com.checkout_service.service;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;

@Service
public class PaymentService {
    @Autowired
    private RazorpayClient client;

    // ... your existing createTransaction method ...

    public void processSuccessfulPayment(String receiptId) {
        // TODO: Later, we will add orderRepository.updateStatus(receiptId, "PAID") here.
        
        System.out.println("---------------------------------------");
        System.out.println("REAL-TIME UPDATE: Payment Verified!");
        System.out.println("Snowflake ID from Receipt: " + receiptId);
        System.out.println("Status: Success (Marking as PAID in logs)");
        System.out.println("---------------------------------------");
    }
    public String createTransaction(long amount, String myInternalId) throws RazorpayException {
    JSONObject options = new JSONObject();
    options.put("amount", amount * 100); // Razorpay expects Paise
    options.put("currency", "INR");
    options.put("receipt", myInternalId); // Your Snowflake ID
    System.out.println("options->"+options.toString());
    Order order = client.orders.create(options);
    System.out.println("order->"+order.toString());
    return order.get("id"); // This returns "order_SInR..."
}
}
