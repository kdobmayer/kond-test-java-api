package com.kond.orders.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kond.orders.dto.CreateOrderRequest;
import com.kond.orders.dto.CreateShipmentRequest;
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
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ShippingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private Long orderId;

    @BeforeEach
    void setUp() throws Exception {
        ProductRequest productReq = new ProductRequest();
        productReq.setName("Shipping Item");
        productReq.setSku("SKU-SHIP-001");
        productReq.setPrice(new BigDecimal("49.99"));
        productReq.setStockQuantity(100);

        String productResponse = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productReq)))
                .andReturn().getResponse().getContentAsString();
        Long productId = objectMapper.readTree(productResponse).get("id").asLong();

        CustomerRequest customerReq = new CustomerRequest();
        customerReq.setFirstName("Ship");
        customerReq.setLastName("Tester");
        customerReq.setEmail("ship@example.com");

        String customerResponse = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(customerReq)))
                .andReturn().getResponse().getContentAsString();
        Long customerId = objectMapper.readTree(customerResponse).get("id").asLong();

        CreateOrderRequest orderReq = new CreateOrderRequest();
        orderReq.setCustomerId(customerId);
        orderReq.setShippingAddress("123 Main St, US");

        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setProductId(productId);
        item.setQuantity(1);
        orderReq.setItems(List.of(item));

        String orderResponse = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderReq)))
                .andReturn().getResponse().getContentAsString();
        orderId = objectMapper.readTree(orderResponse).get("id").asLong();

        UpdateOrderStatusRequest confirm = new UpdateOrderStatusRequest();
        confirm.setStatus("CONFIRMED");
        mockMvc.perform(put("/api/orders/" + orderId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirm)))
                .andExpect(status().isOk());
    }

    @Test
    void calculateRates_negativeWeight_returns400() throws Exception {
        mockMvc.perform(post("/api/shipping/rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originAddress": "123 Main St, US",
                                  "destinationAddress": "456 Oak Ave, US",
                                  "weight": -1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.weight").value("Weight must be greater than or equal to zero"));
    }

    @Test
    void updateShipmentStatus_missingStatus_returns400() throws Exception {
        Long shipmentId = createShipmentAndGetId();

        mockMvc.perform(put("/api/shipping/shipments/" + shipmentId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Status is required"));
    }

    @Test
    void updateShipmentStatus_invalidStatus_returns400() throws Exception {
        Long shipmentId = createShipmentAndGetId();

        mockMvc.perform(put("/api/shipping/shipments/" + shipmentId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "LOST"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid shipment status: LOST"));
    }

    private Long createShipmentAndGetId() throws Exception {
        CreateShipmentRequest request = new CreateShipmentRequest();
        request.setOrderId(orderId);
        request.setCarrier("Standard Post");
        request.setOriginAddress("Warehouse, US");

        String shipmentResponse = mockMvc.perform(post("/api/shipping/shipments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(shipmentResponse).get("id").asLong();
    }
}
