package com.kond.orders.service;

import com.kond.orders.dto.*;
import com.kond.orders.exception.InvalidStateTransitionException;
import com.kond.orders.exception.InsufficientStockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FulfillmentServiceTest {

    @Autowired
    private FulfillmentService fulfillmentService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ShippingRateCalculator shippingRateCalculator;

    private Long customerId;
    private Long productId;

    @BeforeEach
    void setUp() {
        CustomerRequest custReq = new CustomerRequest();
        custReq.setFirstName("Fulfill");
        custReq.setLastName("Tester");
        custReq.setEmail("fulfill-test@example.com");
        customerId = customerService.createCustomer(custReq).getId();

        ProductRequest prodReq = new ProductRequest();
        prodReq.setName("Test Widget");
        prodReq.setSku("SKU-FULFILL-001");
        prodReq.setPrice(new BigDecimal("25.00"));
        prodReq.setStockQuantity(10);
        prodReq.setWeight(new BigDecimal("2.00"));
        productId = productService.createProduct(prodReq).getId();
    }

    @Test
    void fulfill_allInStock_returnsPlan() {
        Long orderId = orderService.createOrder(buildOrderRequest(productId, 3)).getId();

        FulfillmentPlan plan = fulfillmentService.fulfill(orderId);

        assertEquals(1, plan.getReservedItems().size());
        assertTrue(plan.getShippingRate().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(plan.getEstimatedDelivery().isAfter(LocalDateTime.now()));
        assertEquals(0, productService.getProduct(productId).getReservedQuantity());
    }

    @Test
    void fulfill_insufficientStock_throws() {
        ProductRequest lowStockReq = new ProductRequest();
        lowStockReq.setName("Low Stock Widget");
        lowStockReq.setSku("SKU-FULFILL-LOW");
        lowStockReq.setPrice(new BigDecimal("10.00"));
        lowStockReq.setStockQuantity(1);
        Long lowStockProductId = productService.createProduct(lowStockReq).getId();

        Long orderId = orderService.createOrder(buildOrderRequest(lowStockProductId, 2)).getId();

        assertThrows(InsufficientStockException.class, () -> fulfillmentService.fulfill(orderId));
    }

    @Test
    void fulfill_multipleItems_oneOutOfStock_throws() {
        ProductRequest prodReq2 = new ProductRequest();
        prodReq2.setName("Second Widget");
        prodReq2.setSku("SKU-FULFILL-002");
        prodReq2.setPrice(new BigDecimal("15.00"));
        prodReq2.setStockQuantity(1);
        Long productId2 = productService.createProduct(prodReq2).getId();

        CreateOrderRequest orderReq = new CreateOrderRequest();
        orderReq.setCustomerId(customerId);
        orderReq.setShippingAddress("456 Oak Ave, US");

        CreateOrderRequest.OrderItemRequest item1 = new CreateOrderRequest.OrderItemRequest();
        item1.setProductId(productId);
        item1.setQuantity(5);

        CreateOrderRequest.OrderItemRequest item2 = new CreateOrderRequest.OrderItemRequest();
        item2.setProductId(productId2);
        item2.setQuantity(2); // only 1 in stock

        orderReq.setItems(List.of(item1, item2));

        Long orderId = orderService.createOrder(orderReq).getId();

        assertThrows(InsufficientStockException.class, () -> fulfillmentService.fulfill(orderId));

        // Pre-check must throw before any reservation is made
        assertEquals(0, productService.getProduct(productId).getReservedQuantity());
    }

    @Test
    void fulfill_correctShippingRate_calculated() {
        // product weight=2.00, quantity=3 → totalWeight=6.00, domestic shipping
        Long orderId = orderService.createOrder(buildOrderRequest(productId, 3)).getId();

        FulfillmentPlan plan = fulfillmentService.fulfill(orderId);

        BigDecimal expectedRate = shippingRateCalculator.calculateSingleRate(
                new BigDecimal("6.00"), "STANDARD", false);
        assertEquals(0, plan.getShippingRate().compareTo(expectedRate));
    }

    @Test
    void fulfill_nonCreatedOrder_throws() {
        Long orderId = orderService.createOrder(buildOrderRequest(productId, 1)).getId();

        UpdateOrderStatusRequest updateRequest = new UpdateOrderStatusRequest();
        updateRequest.setStatus("CONFIRMED");
        updateRequest.setReason("Ready to ship");
        orderService.updateStatus(orderId, updateRequest);

        assertThrows(InvalidStateTransitionException.class, () -> fulfillmentService.fulfill(orderId));
    }

    private CreateOrderRequest buildOrderRequest(Long pId, int quantity) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId(customerId);
        request.setShippingAddress("456 Oak Ave, US");

        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setProductId(pId);
        item.setQuantity(quantity);
        request.setItems(List.of(item));

        return request;
    }
}
