package com.checkout_service.component;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.checkout_service.domain.BaseEvent;
import com.checkout_service.domain.InventoryFailedEvent;
import com.checkout_service.domain.InventoryReservedEvent;
import com.checkout_service.service.CheckoutService;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class InventoryEventListener {

    private final ObjectMapper objectMapper;
    private final CheckoutService checkoutService;

    public InventoryEventListener(ObjectMapper objectMapper,
                                  CheckoutService checkoutService) {
        this.objectMapper = objectMapper;
        this.checkoutService = checkoutService;
    }
    @KafkaListener(topics = "inventory-events")
public void listen(String message, Acknowledgment ack) {

    try {

        BaseEvent base =
                objectMapper.readValue(message, BaseEvent.class);

        switch (base.eventType()) {

            case "InventoryReservedEvent" -> {
                InventoryReservedEvent event =
                        objectMapper.readValue(
                                message,
                                InventoryReservedEvent.class
                        );
                checkoutService.handleInventoryReserved(
                        event.orderId()
                );
            }

            case "InventoryFailedEvent" -> {
                InventoryFailedEvent event =
                        objectMapper.readValue(
                                message,
                                InventoryFailedEvent.class
                        );
                checkoutService.handleInventoryFailed(
                        event.orderId(),
                        event.reason()
                );
            }
        }

        ack.acknowledge();

    } catch (Exception e) {
        throw new RuntimeException(e);
    }
}

}
