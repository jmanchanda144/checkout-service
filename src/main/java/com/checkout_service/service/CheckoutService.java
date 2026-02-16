package com.checkout_service.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.checkout_service.domain.CheckoutAddress;
import com.checkout_service.domain.CheckoutItem;
import com.checkout_service.domain.CheckoutOrder;
import com.checkout_service.domain.CheckoutOutboxEvent;
import com.checkout_service.domain.CheckoutStatus;
import com.checkout_service.domain.OrderCreatedEvent;
import com.checkout_service.domain.OutboxStatus;
import com.checkout_service.domain.PaymentFailedEvent;
import com.checkout_service.domain.PaymentMethod;
import com.checkout_service.domain.PaymentSuccessEvent;
import com.checkout_service.dto.CheckoutInitResponse;
import com.checkout_service.dto.CheckoutStatusResponse;
import com.checkout_service.dto.CreateCheckoutRequest;
import com.checkout_service.dto.ProductResponse;
import com.checkout_service.id.SnowflakeIdGenerator;
import com.checkout_service.repo.CheckoutAddressRepository;
import com.checkout_service.repo.CheckoutItemRepository;
import com.checkout_service.repo.CheckoutOrderRepository;
import com.checkout_service.repo.CheckoutOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.razorpay.RazorpayException;

@Service
public class CheckoutService {

    private final CheckoutOrderRepository orderRepo;
    private final CheckoutItemRepository itemRepo;
    private final CheckoutAddressRepository addressRepo;
    private final ProductClient productClient;
    private final SnowflakeIdGenerator idGenerator;
    private final PaymentService paymentService;
    private final CheckoutOutboxRepository outboxRepo;
    private final ObjectMapper objectMapper;

    public CheckoutService(
            CheckoutOrderRepository orderRepo,
            CheckoutItemRepository itemRepo,
            CheckoutAddressRepository addressRepo,
            ProductClient productClient,
            SnowflakeIdGenerator idGenerator,
            PaymentService paymentService,
            CheckoutOutboxRepository outboxRepo,
            ObjectMapper objectMapper) {

        this.orderRepo = orderRepo;
        this.itemRepo = itemRepo;
        this.addressRepo = addressRepo;
        this.productClient = productClient;
        this.idGenerator = idGenerator;
        this.paymentService = paymentService;
        this.outboxRepo = outboxRepo;
        this.objectMapper = objectMapper;
    }

    // =====================================
    // ENTRY POINT (NO DB TRANSACTION HERE)
    // =====================================
public CheckoutInitResponse createAndInitiate(CreateCheckoutRequest request)
        throws RazorpayException {

    if (request.items() == null || request.items().isEmpty()) {
        throw new IllegalArgumentException("No items");
    }

    List<Long> productIds = request.items()
            .stream()
            .map(CreateCheckoutRequest.Item::productId)
            .toList();

    List<ProductResponse> products =
            productClient.getProductsBulk(productIds);

    Map<Long, BigDecimal> priceMap =
            products.stream()
                    .collect(Collectors.toMap(
                            ProductResponse::id,
                            ProductResponse::price
                    ));

    long checkoutId = idGenerator.nextId();

    BigDecimal total = calculateTotal(request, priceMap);

    createCheckout(
            checkoutId,
            request,
            priceMap,
            total
    );

     return new CheckoutInitResponse(
            String.valueOf(checkoutId),
            null,
            total.multiply(BigDecimal.valueOf(100)).longValue(),
            "rzp_test_SElR6dCtE9ARWL"
    );
}


    // =====================================
    // DB ONLY TRANSACTION
    // =====================================
    @Transactional
    public void createCheckout(
        Long checkoutId,
        CreateCheckoutRequest request,
        Map<Long, BigDecimal> priceMap,
        BigDecimal total){

        Instant now = Instant.now();

        // 1️⃣ Save Order
        CheckoutOrder order = new CheckoutOrder();
        order.setId(checkoutId);
        order.setUserId(request.userId());
        order.setStatus(CheckoutStatus.CREATED);
        order.setPaymentMethod(request.paymentMethod());
        order.setTotalAmount(total);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        orderRepo.save(order);

        // 2️⃣ Save Items
        for (var i : request.items()) {

            BigDecimal price = priceMap.get(i.productId());

            CheckoutItem item = new CheckoutItem();
            item.setId(idGenerator.nextId());
            item.setCheckoutId(checkoutId);
            item.setProductId(i.productId());
            item.setQuantity(i.quantity());
            item.setPrice(price);

            itemRepo.save(item);
        }

        // 3️⃣ Save Address
        var addr = request.address();

        CheckoutAddress address = new CheckoutAddress();
        address.setId(idGenerator.nextId());
        address.setCheckoutId(checkoutId);
        address.setFullName(addr.fullName());
        address.setPhone(addr.phone());
        address.setAddressLine1(addr.addressLine1());
        address.setCity(addr.city());
        address.setState(addr.state());
        address.setPincode(addr.pincode());

        addressRepo.save(address);

        // 4️⃣ Create Outbox Event (AFTER full order is built)
        List<OrderCreatedEvent.Item> eventItems =
                request.items().stream()
                        .map(i -> new OrderCreatedEvent.Item(
                                i.productId(),
                                i.quantity()
                        ))
                        .toList();

        OrderCreatedEvent event =
                new OrderCreatedEvent(
                        checkoutId,
                        request.userId(),
                        eventItems
                );

        String payload;
        try {
        payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
        throw new RuntimeException(
                "Failed to serialize OrderCreatedEvent",
                e
        );
        }

        CheckoutOutboxEvent outbox = new CheckoutOutboxEvent();
        outbox.setId(idGenerator.nextId());
        outbox.setAggregateType("ORDER");
        outbox.setAggregateId(checkoutId);
        outbox.setEventType("OrderCreatedEvent");
        outbox.setTopic("order-events");
        outbox.setPayload(payload);
        outbox.setStatus(OutboxStatus.PENDING);
        outbox.setRetryCount(0);
        outbox.setCreatedAt(now);

        outboxRepo.save(outbox);
    }

    private BigDecimal calculateTotal(
            CreateCheckoutRequest request,
            Map<Long, BigDecimal> priceMap) {

        BigDecimal total = BigDecimal.ZERO;

        for (var i : request.items()) {

            BigDecimal price = priceMap.get(i.productId());

            if (price == null) {
                throw new RuntimeException(
                        "Product not found: " + i.productId());
            }

            BigDecimal itemTotal =
                    price.multiply(BigDecimal.valueOf(i.quantity()));

            total = total.add(itemTotal);
        }

        return total;
    }

    // =====================================
    // PAYMENT CALLBACKS
    // =====================================
        private String serialize(Object event) {
        try {
                return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
                throw new RuntimeException("Serialization failed", e);
        }
        }
        @Transactional
        public void handlePaymentSuccess(Long checkoutId, String paymentId) {

        CheckoutOrder order =
                orderRepo.findById(checkoutId).orElseThrow();

        if (order.getStatus() != CheckoutStatus.PAYMENT_PENDING)
                return;

        order.setRazorpayPaymentId(paymentId);
        order.setStatus(CheckoutStatus.PAYMENT_SUCCESS);
        order.setUpdatedAt(Instant.now());

        // 🔥 ADD THIS
        productClient.confirm(checkoutId);

        PaymentSuccessEvent event =
                new PaymentSuccessEvent(checkoutId, paymentId);

        String payload = serialize(event);

        CheckoutOutboxEvent outbox = new CheckoutOutboxEvent();
        outbox.setId(idGenerator.nextId());
        outbox.setAggregateType("ORDER");
        outbox.setAggregateId(checkoutId);
        outbox.setEventType("PaymentSuccessEvent");
        outbox.setTopic("payment-events");
        outbox.setPayload(payload);
        outbox.setStatus(OutboxStatus.PENDING);
        outbox.setCreatedAt(Instant.now());

        outboxRepo.save(outbox);
        }
        @Transactional
        public void handlePaymentFailed(Long checkoutId) {

        CheckoutOrder order =
                orderRepo.findById(checkoutId).orElseThrow();

        if (order.getStatus() != CheckoutStatus.PAYMENT_PENDING)
                return;

        order.setStatus(CheckoutStatus.PAYMENT_FAILED);
        order.setUpdatedAt(Instant.now());

        // 🔥 ADD THIS
        productClient.release(checkoutId);

        PaymentFailedEvent event =
                new PaymentFailedEvent(checkoutId);

        String payload = serialize(event);

        CheckoutOutboxEvent outbox = new CheckoutOutboxEvent();
        outbox.setId(idGenerator.nextId());
        outbox.setAggregateType("ORDER");
        outbox.setAggregateId(checkoutId);
        outbox.setEventType("PaymentFailedEvent");
        outbox.setTopic("payment-events");
        outbox.setPayload(payload);
        outbox.setStatus(OutboxStatus.PENDING);
        outbox.setCreatedAt(Instant.now());

        outboxRepo.save(outbox);
        }
        @Transactional
        public void handleInventoryReserved(Long orderId) {

        CheckoutOrder order =
                orderRepo.findById(orderId).orElseThrow();

        if (order.getStatus() != CheckoutStatus.CREATED) {
                return;
        }

        order.setStatus(CheckoutStatus.PAYMENT_PENDING);
        order.setUpdatedAt(Instant.now());

        if (order.getPaymentMethod() == PaymentMethod.RAZORPAY) {

                long amountInPaise =
                        order.getTotalAmount()
                                .multiply(BigDecimal.valueOf(100))
                                .longValueExact();

                try {

                String razorpayOrderId =
                        paymentService.createTransaction(
                                amountInPaise,
                                String.valueOf(orderId)
                        );

                order.setRazorpayOrderId(razorpayOrderId);

                } catch (Exception e) {
                throw new RuntimeException("Payment initiation failed", e);
                }
        }
        }
        @Transactional
        public void handleInventoryFailed(Long orderId, String reason) {

        CheckoutOrder order =
                orderRepo.findById(orderId).orElseThrow();

        if (order.getStatus() != CheckoutStatus.CREATED) {
                return; // idempotent
        }

        order.setStatus(CheckoutStatus.CANCELLED);
        order.setUpdatedAt(Instant.now());

        // optional: log reason
        System.out.println("Inventory failed for order "
                + orderId + " reason: " + reason);
        }

     @Transactional(readOnly = true)
        public CheckoutStatusResponse getOrder(Long id) {

        CheckoutOrder order =
                orderRepo.findById(id).orElse(null);
                if (order == null) {
                        return new CheckoutStatusResponse(
                                String.valueOf(id),
                                CheckoutStatus.CREATED.name(),
                                null,
                                null,
                                "0"
                        );
                }
        return new CheckoutStatusResponse(
                 String.valueOf(order.getId()),
                order.getStatus().name(),
                order.getRazorpayOrderId(),
                order.getRazorpayPaymentId(),
                order.getTotalAmount().toString()
        );
        }

}
