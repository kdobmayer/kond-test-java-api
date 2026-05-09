package com.kond.orders.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kond.orders.dto.*;
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
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private Long productId;
    private Long orderId;

    @BeforeEach
    void setUp() throws Exception {
        // Create product
        ProductRequest productReq = new ProductRequest();
        productReq.setName("Inventory Item");
        productReq.setSku("SKU-INV-001");
        productReq.setPrice(new BigDecimal("25.00"));
        productReq.setStockQuantity(50);

        String prodResp = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productReq)))
                .andReturn().getResponse().getContentAsString();
        productId = objectMapper.readTree(prodResp).get("id").asLong();

        // Create customer
        CustomerRequest custReq = new CustomerRequest();
        custReq.setFirstName("Inv");
        custReq.setLastName("Test");
        custReq.setEmail("inv@example.com");

        String custResp = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(custReq)))
                .andReturn().getResponse().getContentAsString();
        Long customerId = objectMapper.readTree(custResp).get("id").asLong();

        // Create order
        CreateOrderRequest orderReq = new CreateOrderRequest();
        orderReq.setCustomerId(customerId);
        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setProductId(productId);
        item.setQuantity(1);
        orderReq.setItems(List.of(item));

        String orderResp = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderReq)))
                .andReturn().getResponse().getContentAsString();
        orderId = objectMapper.readTree(orderResp).get("id").asLong();
    }

    @Test
    void reserveStock_success() throws Exception {
        InventoryReserveRequest request = new InventoryReserveRequest();
        request.setProductId(productId);
        request.setOrderId(orderId);
        request.setQuantity(10);

        mockMvc.perform(post("/api/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationId").exists())
                .andExpect(jsonPath("$.quantity").value(10));
    }

    @Test
    void reserveStock_insufficientStock_returns422() throws Exception {
        InventoryReserveRequest request = new InventoryReserveRequest();
        request.setProductId(productId);
        request.setOrderId(orderId);
        request.setQuantity(999);

        mockMvc.perform(post("/api/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void checkAvailability_success() throws Exception {
        mockMvc.perform(get("/api/inventory/availability/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId))
                .andExpect(jsonPath("$.stockQuantity").value(50))
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void releaseReservation_success() throws Exception {
        // Reserve first
        InventoryReserveRequest request = new InventoryReserveRequest();
        request.setProductId(productId);
        request.setOrderId(orderId);
        request.setQuantity(5);

        String resp = mockMvc.perform(post("/api/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        Long reservationId = objectMapper.readTree(resp).get("reservationId").asLong();

        // Release
        mockMvc.perform(post("/api/inventory/release/" + reservationId))
                .andExpect(status().isNoContent());

        // Verify availability restored
        mockMvc.perform(get("/api/inventory/availability/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(50));
    }
}
