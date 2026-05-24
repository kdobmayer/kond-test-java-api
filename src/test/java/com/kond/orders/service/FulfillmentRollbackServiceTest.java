package com.kond.orders.service;

import com.kond.orders.dto.*;
import com.kond.orders.entity.ShipmentStatus;
import com.kond.orders.exception.InvalidStateTransitionException;
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
class FulfillmentRollbackServiceTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ShippingService shippingService;

    private Long customerId;
    private Long productId;

    @BeforeEach
    void setUp() {
        CustomerRequest custReq = new CustomerRequest();
        custReq.setFirstName("Rollback");
        custReq.setLastName("Tester");
        custReq.setEmail("rollback-test@example.com");
        customerId = customerService.createCustomer(custReq).getId();

        ProductRequest prodReq = new ProductRequest();
        prodReq.setName("Rollback Widget");
        prodReq.setSku("SKU-ROLLBACK-001");
        prodReq.setPrice(new BigDecimal("20.00"));
        prodReq.setStockQuantity(20);
        prodReq.setWeight(new BigDecimal("1.50"));
        productId = productService.createProduct(prodReq).getId();
    }

    @Test
    void cancel_fulfilledOrder_releasesStock() {
        Long orderId = orderService.createOrder(buildOrderRequest(3)).getId();
        orderService.fulfillOrder(orderId);

        assertEquals(3, productService.getProduct(productId).getReservedQuantity());

        orderService.cancelOrder(orderId, "Customer request");

        assertEquals(0, productService.getProduct(productId).getReservedQuantity());
    }

    @Test
    void cancel_fulfilledOrder_cancelsShipment() {
        Long orderId = orderService.createOrder(buildOrderRequest(2)).getId();
        orderService.fulfillOrder(orderId);

        orderService.cancelOrder(orderId, "Changed mind");

        ShipmentResponse shipment = shippingService.getShipmentByOrder(orderId);
        assertEquals(ShipmentStatus.CANCELLED, shipment.getStatus());
    }

    @Test
    void cancel_unfulfilledOrder_isNoOp() {
        Long orderId = orderService.createOrder(buildOrderRequest(1)).getId();

        OrderResponse cancelled = assertDoesNotThrow(
                () -> orderService.cancelOrder(orderId, "Never fulfilled"));

        assertEquals(com.kond.orders.entity.OrderStatus.CANCELLED, cancelled.getStatus());
        assertEquals(0, productService.getProduct(productId).getReservedQuantity());
    }

    @Test
    void cancel_terminalOrder_throwsInvalidTransition() {
        Long orderId = orderService.createOrder(buildOrderRequest(1)).getId();
        orderService.fulfillOrder(orderId);

        UpdateOrderStatusRequest ship = new UpdateOrderStatusRequest();
        ship.setStatus("SHIPPED");
        orderService.updateStatus(orderId, ship);

        UpdateOrderStatusRequest deliver = new UpdateOrderStatusRequest();
        deliver.setStatus("DELIVERED");
        orderService.updateStatus(orderId, deliver);

        assertThrows(InvalidStateTransitionException.class,
                () -> orderService.cancelOrder(orderId, "Too late"));
    }

    @Test
    void cancel_confirmedOrder_doesNotRewriteReturnedShipment() {
        Long orderId = orderService.createOrder(buildOrderRequest(1)).getId();
        orderService.fulfillOrder(orderId);

        ShipmentResponse shipment = shippingService.getShipmentByOrder(orderId);
        shippingService.updateShipmentStatus(shipment.getId(), ShipmentStatus.RETURNED);

        orderService.cancelOrder(orderId, "Carrier returned package");

        ShipmentResponse updated = shippingService.getShipmentByOrder(orderId);
        assertEquals(ShipmentStatus.RETURNED, updated.getStatus());
    }

    private CreateOrderRequest buildOrderRequest(int quantity) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId(customerId);
        request.setShippingAddress("789 Pine Rd, US");

        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setProductId(productId);
        item.setQuantity(quantity);
        request.setItems(List.of(item));

        return request;
    }
}
