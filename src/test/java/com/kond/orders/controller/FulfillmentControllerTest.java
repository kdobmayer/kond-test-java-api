package com.kond.orders.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kond.orders.dto.CreateOrderRequest;
import com.kond.orders.dto.CustomerRequest;
import com.kond.orders.dto.ProductRequest;
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
class FulfillmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private Long customerId;
    private Long productId;

    @BeforeEach
    void setUp() throws Exception {
        CustomerRequest custReq = new CustomerRequest();
        custReq.setFirstName("Fulfill");
        custReq.setLastName("Tester");
        custReq.setEmail("fulfill-ctrl@example.com");

        String custResp = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(custReq)))
                .andReturn().getResponse().getContentAsString();
        customerId = objectMapper.readTree(custResp).get("id").asLong();

        ProductRequest prodReq = new ProductRequest();
        prodReq.setName("Fulfill Product");
        prodReq.setSku("SKU-FULFILL-CTRL-001");
        prodReq.setPrice(new BigDecimal("50.00"));
        prodReq.setStockQuantity(10);
        prodReq.setWeight(new BigDecimal("1.00"));

        String prodResp = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prodReq)))
                .andReturn().getResponse().getContentAsString();
        productId = objectMapper.readTree(prodResp).get("id").asLong();
    }

    @Test
    void fulfill_success() throws Exception {
        Long orderId = createOrder(productId, 2);

        mockMvc.perform(post("/api/orders/" + orderId + "/fulfill"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shippingRate").exists())
                .andExpect(jsonPath("$.estimatedDelivery").exists())
                .andExpect(jsonPath("$.reservedItems.length()").value(1));

        mockMvc.perform(get("/api/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void fulfill_success_shipmentExists() throws Exception {
        Long orderId = createOrder(productId, 2);

        mockMvc.perform(post("/api/orders/" + orderId + "/fulfill"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/shipping/shipments/order/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.orderId").value(orderId));
    }

    @Test
    void fulfill_outOfStock_returns422() throws Exception {
        ProductRequest lowStock = new ProductRequest();
        lowStock.setName("Low Stock Product");
        lowStock.setSku("SKU-FULFILL-LOW-CTRL");
        lowStock.setPrice(new BigDecimal("10.00"));
        lowStock.setStockQuantity(1);

        String prodResp = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lowStock)))
                .andReturn().getResponse().getContentAsString();
        Long lowStockProductId = objectMapper.readTree(prodResp).get("id").asLong();

        Long orderId = createOrder(lowStockProductId, 2);

        mockMvc.perform(post("/api/orders/" + orderId + "/fulfill"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void fulfill_alreadyConfirmed_returns409() throws Exception {
        Long orderId = createOrder(productId, 1);

        mockMvc.perform(post("/api/orders/" + orderId + "/fulfill"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/" + orderId + "/fulfill"))
                .andExpect(status().isConflict());
    }

    @Test
    void fulfill_orderNotFound_returns404() throws Exception {
        mockMvc.perform(post("/api/orders/9999/fulfill"))
                .andExpect(status().isNotFound());
    }

    private Long createOrder(Long pId, int quantity) throws Exception {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setCustomerId(customerId);
        req.setShippingAddress("123 Main St, US");

        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setProductId(pId);
        item.setQuantity(quantity);
        req.setItems(List.of(item));

        String resp = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("id").asLong();
    }
}
