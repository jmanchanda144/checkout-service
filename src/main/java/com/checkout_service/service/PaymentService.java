package com.checkout_service.service;

import org.json.JSONObject;
import org.springframework.stereotype.Service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;

@Service
public class PaymentService {

    private final RazorpayClient client;

    public PaymentService(RazorpayClient client) {
        this.client = client;
    }

    public String createTransaction(long amountInPaise, String receiptId)
            throws RazorpayException {

        JSONObject options = new JSONObject();
        options.put("amount", amountInPaise); // already in paise
        options.put("currency", "INR");
        options.put("receipt", receiptId);

        Order order = client.orders.create(options);

        return order.get("id");
    }
}
