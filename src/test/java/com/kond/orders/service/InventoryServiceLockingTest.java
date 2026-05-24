package com.kond.orders.service;

import com.kond.orders.dto.*;
import com.kond.orders.entity.Product;
import com.kond.orders.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class InventoryServiceLockingTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private ProductService productService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long productId;
    private Long orderId;

    @BeforeEach
    void setUp() {
        CustomerRequest custReq = new CustomerRequest();
        custReq.setFirstName("Lock");
        custReq.setLastName("Test");
        custReq.setEmail("lock-test@example.com");
        Long customerId = customerService.createCustomer(custReq).getId();

        ProductRequest prodReq = new ProductRequest();
        prodReq.setName("Lock Product");
        prodReq.setSku("SKU-LOCK-001");
        prodReq.setPrice(new BigDecimal("10.00"));
        prodReq.setStockQuantity(100);
        productId = productService.createProduct(prodReq).getId();

        CreateOrderRequest orderReq = new CreateOrderRequest();
        orderReq.setCustomerId(customerId);
        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setProductId(productId);
        item.setQuantity(1);
        orderReq.setItems(List.of(item));
        orderId = orderService.createOrder(orderReq).getId();
    }

    @Test
    void versionIncrementsAfterReservation() {
        Product before = productRepository.findById(productId).orElseThrow();
        Long initialVersion = before.getVersion();
        assertNotNull(initialVersion);

        InventoryReserveRequest request = new InventoryReserveRequest();
        request.setProductId(productId);
        request.setOrderId(orderId);
        request.setQuantity(5);
        inventoryService.reserveStock(request);

        Product after = productRepository.findById(productId).orElseThrow();
        assertTrue(after.getVersion() > initialVersion);
    }

    @Test
    void staleVersion_throwsOptimisticLockException() {
        // Load the product — this version is now stale after the JdbcTemplate update below
        Product staleProduct = productRepository.findById(productId).orElseThrow();

        // Simulate a concurrent transaction committing first by advancing the version directly
        jdbcTemplate.update(
                "UPDATE products SET reserved_quantity = 1, version = version + 1 WHERE id = ?",
                productId);

        // Saving the stale entity must fail with an optimistic lock exception
        staleProduct.setReservedQuantity(staleProduct.getReservedQuantity() + 5);
        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> productRepository.saveAndFlush(staleProduct));
    }
}
