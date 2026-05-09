package com.kond.orders.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kond.orders.dto.CreateOrderRequest;
import com.kond.orders.dto.CustomerRequest;
import com.kond.orders.dto.ProductRequest;
import com.kond.orders.dto.UpdateOrderStatusRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private Long customerId;
    private Long productId;

    @BeforeEach
    void setUp() throws Exception {
        // Create a customer
        CustomerRequest customerReq = new CustomerRequest();
        customerReq.setFirstName("Test");
        customerReq.setLastName("User");
        customerReq.setEmail("test@example.com");

        String custResponse = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(customerReq)))
                .andReturn().getResponse().getContentAsString();
        customerId = objectMapper.readTree(custResponse).get("id").asLong();

        // Create a product
        ProductRequest productReq = new ProductRequest();
        productReq.setName("Test Product");
        productReq.setSku("SKU-TEST-001");
        productReq.setPrice(new BigDecimal("49.99"));
        productReq.setStockQuantity(100);

        String prodResponse = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productReq)))
                .andReturn().getResponse().getContentAsString();
        productId = objectMapper.readTree(prodResponse).get("id").asLong();
    }

    @Test
    void createOrder_success() throws Exception {
        CreateOrderRequest request = createOrderRequest();

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalAmount").value(99.98));
    }

    @Test
    void createOrder_invalidCustomer_returns404() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId(9999L);
        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setProductId(productId);
        item.setQuantity(1);
        request.setItems(List.of(item));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void confirmOrder_success() throws Exception {
        Long orderId = createAndGetOrderId();

        UpdateOrderStatusRequest statusReq = new UpdateOrderStatusRequest();
        statusReq.setStatus("CONFIRMED");

        mockMvc.perform(put("/api/orders/" + orderId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void invalidTransition_returns409() throws Exception {
        Long orderId = createAndGetOrderId();

        UpdateOrderStatusRequest statusReq = new UpdateOrderStatusRequest();
        statusReq.setStatus("DELIVERED");

        mockMvc.perform(put("/api/orders/" + orderId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusReq)))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelOrder_fromCreated_success() throws Exception {
        Long orderId = createAndGetOrderId();

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Changed my mind\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelOrder_fromShipped_returns409() throws Exception {
        Long orderId = createAndGetOrderId();

        // Confirm
        UpdateOrderStatusRequest confirm = new UpdateOrderStatusRequest();
        confirm.setStatus("CONFIRMED");
        mockMvc.perform(put("/api/orders/" + orderId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirm)));

        // Ship
        UpdateOrderStatusRequest ship = new UpdateOrderStatusRequest();
        ship.setStatus("SHIPPED");
        mockMvc.perform(put("/api/orders/" + orderId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ship)));

        // Try cancel
        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Too late\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void getOrdersByStatus() throws Exception {
        createAndGetOrderId();

        mockMvc.perform(get("/api/orders").param("status", "CREATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    private Long createAndGetOrderId() throws Exception {
        CreateOrderRequest request = createOrderRequest();
        String response = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private CreateOrderRequest createOrderRequest() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId(customerId);
        request.setShippingAddress("123 Main St, US");

        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setProductId(productId);
        item.setQuantity(2);
        request.setItems(List.of(item));

        return request;
    }
}
