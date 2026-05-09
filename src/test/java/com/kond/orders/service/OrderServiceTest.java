package com.kond.orders.service;

import com.kond.orders.dto.*;
import com.kond.orders.entity.OrderStatus;
import com.kond.orders.exception.InvalidStateTransitionException;
import com.kond.orders.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private ProductService productService;

    private Long customerId;
    private Long productId;

    @BeforeEach
    void setUp() {
        CustomerRequest custReq = new CustomerRequest();
        custReq.setFirstName("Order");
        custReq.setLastName("Tester");
        custReq.setEmail("order-test@example.com");
        customerId = customerService.createCustomer(custReq).getId();

        ProductRequest prodReq = new ProductRequest();
        prodReq.setName("Order Product");
        prodReq.setSku("SKU-ORD-001");
        prodReq.setPrice(new BigDecimal("15.00"));
        prodReq.setStockQuantity(200);
        productId = productService.createProduct(prodReq).getId();
    }

    @Test
    void createOrder_calculatesTotal() {
        CreateOrderRequest request = buildOrderRequest(3);
        OrderResponse response = orderService.createOrder(request);

        assertEquals(OrderStatus.CREATED, response.getStatus());
        assertEquals(new BigDecimal("45.00"), response.getTotalAmount());
        assertEquals(1, response.getItems().size());
    }

    @Test
    void orderStateMachine_fullFlow() {
        CreateOrderRequest request = buildOrderRequest(2);
        OrderResponse order = orderService.createOrder(request);

        // CREATED -> CONFIRMED
        UpdateOrderStatusRequest confirm = new UpdateOrderStatusRequest();
        confirm.setStatus("CONFIRMED");
        OrderResponse confirmed = orderService.updateStatus(order.getId(), confirm);
        assertEquals(OrderStatus.CONFIRMED, confirmed.getStatus());

        // CONFIRMED -> SHIPPED
        UpdateOrderStatusRequest ship = new UpdateOrderStatusRequest();
        ship.setStatus("SHIPPED");
        OrderResponse shipped = orderService.updateStatus(order.getId(), ship);
        assertEquals(OrderStatus.SHIPPED, shipped.getStatus());

        // SHIPPED -> DELIVERED
        UpdateOrderStatusRequest deliver = new UpdateOrderStatusRequest();
        deliver.setStatus("DELIVERED");
        OrderResponse delivered = orderService.updateStatus(order.getId(), deliver);
        assertEquals(OrderStatus.DELIVERED, delivered.getStatus());
    }

    @Test
    void cancelOrder_fromCreated() {
        CreateOrderRequest request = buildOrderRequest(1);
        OrderResponse order = orderService.createOrder(request);

        OrderResponse cancelled = orderService.cancelOrder(order.getId(), "No longer needed");
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void cancelOrder_fromConfirmed_releasesInventory() {
        CreateOrderRequest request = buildOrderRequest(5);
        OrderResponse order = orderService.createOrder(request);

        UpdateOrderStatusRequest confirm = new UpdateOrderStatusRequest();
        confirm.setStatus("CONFIRMED");
        orderService.updateStatus(order.getId(), confirm);

        // Cancel should release reservations
        OrderResponse cancelled = orderService.cancelOrder(order.getId(), "Customer request");
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void cancelOrder_fromShipped_throws() {
        CreateOrderRequest request = buildOrderRequest(1);
        OrderResponse order = orderService.createOrder(request);

        UpdateOrderStatusRequest confirm = new UpdateOrderStatusRequest();
        confirm.setStatus("CONFIRMED");
        orderService.updateStatus(order.getId(), confirm);

        UpdateOrderStatusRequest ship = new UpdateOrderStatusRequest();
        ship.setStatus("SHIPPED");
        orderService.updateStatus(order.getId(), ship);

        assertThrows(InvalidStateTransitionException.class,
                () -> orderService.cancelOrder(order.getId(), "Too late"));
    }

    @Test
    void invalidTransition_throws() {
        CreateOrderRequest request = buildOrderRequest(1);
        OrderResponse order = orderService.createOrder(request);

        UpdateOrderStatusRequest deliver = new UpdateOrderStatusRequest();
        deliver.setStatus("DELIVERED");

        assertThrows(InvalidStateTransitionException.class,
                () -> orderService.updateStatus(order.getId(), deliver));
    }

    @Test
    void getOrder_notFound_throws() {
        assertThrows(ResourceNotFoundException.class, () -> orderService.getOrder(9999L));
    }

    @Test
    void getOrdersByCustomer() {
        CreateOrderRequest request = buildOrderRequest(1);
        orderService.createOrder(request);
        orderService.createOrder(request);

        List<OrderResponse> orders = orderService.getOrdersByCustomer(customerId);
        assertEquals(2, orders.size());
    }

    private CreateOrderRequest buildOrderRequest(int quantity) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId(customerId);
        request.setShippingAddress("456 Oak Ave, US");

        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setProductId(productId);
        item.setQuantity(quantity);
        request.setItems(List.of(item));

        return request;
    }
}
