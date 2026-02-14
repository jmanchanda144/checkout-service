package com.checkout_service.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.checkout_service.domain.CheckoutAddress;
import com.checkout_service.domain.CheckoutItem;
import com.checkout_service.domain.CheckoutOrder;
import com.checkout_service.domain.CheckoutStatus;
import com.checkout_service.domain.OrderCreatedEvent;
import com.checkout_service.domain.PaymentMethod;
import com.checkout_service.dto.BulkReserveRequest;
import com.checkout_service.dto.CreateCheckoutRequest;
import com.checkout_service.dto.ProductResponse;
import com.checkout_service.id.SnowflakeIdGenerator;
import com.checkout_service.repo.CheckoutAddressRepository;
import com.checkout_service.repo.CheckoutItemRepository;
import com.checkout_service.repo.CheckoutOrderRepository;
import com.razorpay.RazorpayException;

import jakarta.transaction.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class CheckoutService {

    private final CheckoutOrderRepository orderRepo;
    private final CheckoutItemRepository itemRepo;
    private final CheckoutAddressRepository addressRepo;
    private final ProductClient productClient;
    private final SnowflakeIdGenerator idGenerator;
    private final PaymentService paymentService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;


    public CheckoutService(
            CheckoutOrderRepository orderRepo,
            CheckoutItemRepository itemRepo,
            CheckoutAddressRepository addressRepo,
            ProductClient productClient,
            SnowflakeIdGenerator idGenerator,
            PaymentService paymentService,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {

        this.orderRepo = orderRepo;
        this.itemRepo = itemRepo;
        this.addressRepo = addressRepo;
        this.productClient = productClient;
        this.idGenerator = idGenerator;
        this.paymentService = paymentService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    // =====================================
    // ENTRY POINT
    // =====================================
    @Transactional
    public String createAndInitiate(CreateCheckoutRequest request) throws RazorpayException {

        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("No items");
        }

        // 🔥 1. Fetch product prices BEFORE DB transaction
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

        // 🔥 2. Create checkout inside DB transaction only
        Long id = createCheckout(request, priceMap);

        // 🔥 3. Initiate reservation outside transaction
        try {
            initiateOrder(id);
        } catch (Exception e) {
            // status already updated inside initiateOrder
        }

        CheckoutOrder order = orderRepo.findById(id).orElseThrow();

    if (order.getPaymentMethod() == PaymentMethod.RAZORPAY) {

        long amountInPaise =
                order.getTotalAmount()
                    .multiply(BigDecimal.valueOf(100))
                    .longValueExact();

        String razorpayOrderId;
                razorpayOrderId = paymentService.createTransaction(
                        amountInPaise,
                        id.toString()
                );

        order.setRazorpayOrderId(razorpayOrderId);
        order.setUpdatedAt(Instant.now());

        orderRepo.save(order);

        return razorpayOrderId;
    }

    return id.toString();

    }

    // =====================================
    // DB ONLY TRANSACTION
    // =====================================
@Transactional
public Long createCheckout(
        CreateCheckoutRequest request,
        Map<Long, BigDecimal> priceMap) {

    long checkoutId = idGenerator.nextId();
    Instant now = Instant.now();

    BigDecimal total = BigDecimal.ZERO;

    // 🔥 1. Calculate total FIRST
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

    // 🔥 2. Create order with total already set
    CheckoutOrder order = new CheckoutOrder();
    order.setId(checkoutId);
    order.setUserId(request.userId());
    order.setStatus(CheckoutStatus.CREATED);
    order.setPaymentMethod(request.paymentMethod());
    order.setTotalAmount(total);     // ✅ SET BEFORE SAVE
    order.setCreatedAt(now);
    order.setUpdatedAt(now);

    orderRepo.save(order);
    
    OrderCreatedEvent event =
            new OrderCreatedEvent(order.getId(), order.getUserId());

    String payload = objectMapper.writeValueAsString(event);

    kafkaTemplate.send(
        "order-events",
        order.getId().toString(),   // KEY (important)
        payload
        );

    // 🔥 3. Save items
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

    // 🔥 4. Save address
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

    return checkoutId;
}
    // =====================================
    // RESERVATION FLOW (NO DB TX)
    // =====================================
    @Transactional
    public void initiateOrder(Long checkoutId) {

        CheckoutOrder order =
                orderRepo.findById(checkoutId).orElseThrow();

        if (order.getStatus() != CheckoutStatus.CREATED)
            return;

        List<CheckoutItem> items =
                itemRepo.findByCheckoutId(checkoutId);

        try {

            List<BulkReserveRequest.Item> reserveItems =
                    items.stream()
                            .map(i -> new BulkReserveRequest.Item(
                                    i.getProductId(),
                                    i.getQuantity()))
                            .toList();

            productClient.reserveBulk(checkoutId, reserveItems);

            if (order.getPaymentMethod() == PaymentMethod.COD) {

                productClient.confirm(checkoutId);
                order.setStatus(CheckoutStatus.PAYMENT_SUCCESS);

            } else {

                order.setStatus(CheckoutStatus.PAYMENT_PENDING);
            }

        } catch (Exception e) {

            order.setStatus(CheckoutStatus.CANCELLED);
            productClient.release(checkoutId);
            throw e;
        }

        order.setUpdatedAt(Instant.now());
        orderRepo.save(order);
    }

    // =====================================
    // PAYMENT CALLBACKS
    // =====================================
    @Transactional
    public void handlePaymentSuccess(Long checkoutId) {

        CheckoutOrder order =
                orderRepo.findById(checkoutId).orElseThrow();

        if (order.getStatus() == CheckoutStatus.PAYMENT_SUCCESS)
            return;
        // 🔥 Only allow from PAYMENT_PENDING
        if (order.getStatus() == CheckoutStatus.PAYMENT_PENDING) {
        productClient.confirm(checkoutId);
        order.setStatus(CheckoutStatus.PAYMENT_SUCCESS);
        order.setUpdatedAt(Instant.now());
        }
    }

    @Transactional
    public void handlePaymentFailed(Long checkoutId) {

        CheckoutOrder order =
                orderRepo.findById(checkoutId).orElseThrow();

        if (order.getStatus() == CheckoutStatus.PAYMENT_FAILED)
            return;
        // 🔥 Only allow from PAYMENT_PENDING
        if (order.getStatus() == CheckoutStatus.PAYMENT_PENDING) {
            productClient.release(checkoutId);
            order.setStatus(CheckoutStatus.PAYMENT_FAILED);
            order.setUpdatedAt(Instant.now());
        }
    }
}
