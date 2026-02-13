package com.checkout_service.service;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.checkout_service.dto.BulkReserveRequest;
import com.checkout_service.dto.ProductResponse;

@Service
public class ProductClient {

    private final RestTemplate restTemplate;

    public ProductClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    public List<ProductResponse> getProductsBulk(List<Long> ids) {

        ProductResponse[] response =
                restTemplate.postForObject(
                        "http://localhost:8081/products/bulk",
                        ids,
                        ProductResponse[].class
                );
        if (response == null){
        throw new RuntimeException("Product service unavailable");}

        return Arrays.asList(response);
    }
    public void reserveBulk(Long orderId, List<BulkReserveRequest.Item> items) {

        BulkReserveRequest request =
                new BulkReserveRequest(orderId, items);

        restTemplate.postForEntity(
                "http://localhost:8081/products/reserve-bulk",
                request,
                String.class
        );
    }

    public void confirm(Long orderId) {
        restTemplate.postForEntity(
                "http://localhost:8081/products/confirm?orderId=" + orderId,
                null,
                String.class
        );
    }

    public void release(Long orderId) {
        restTemplate.postForEntity(
                "http://localhost:8081/products/release?orderId=" + orderId,
                null,
                String.class
        );
    }
}

