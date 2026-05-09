package com.kond.orders.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kond.orders.dto.ProductRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createProduct_success() throws Exception {
        ProductRequest request = createProductRequest("Widget", "SKU-001", new BigDecimal("19.99"), 100);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Widget"))
                .andExpect(jsonPath("$.sku").value("SKU-001"))
                .andExpect(jsonPath("$.stockQuantity").value(100));
    }

    @Test
    void createProduct_duplicateSku_returns400() throws Exception {
        ProductRequest request = createProductRequest("Widget", "DUP-SKU", new BigDecimal("19.99"), 50);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getProduct_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/products/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateStock_success() throws Exception {
        ProductRequest request = createProductRequest("Gadget", "SKU-STOCK", new BigDecimal("29.99"), 50);

        String response = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(patch("/api/products/" + id + "/stock")
                        .param("quantity", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity").value(200));
    }

    @Test
    void updateStock_negativeQuantity_returns400() throws Exception {
        ProductRequest request = createProductRequest("Item", "SKU-NEG", new BigDecimal("9.99"), 10);

        String response = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(patch("/api/products/" + id + "/stock")
                        .param("quantity", "-5"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAllProducts_filterByCategory() throws Exception {
        ProductRequest electronics = createProductRequest("Phone", "SKU-PHONE", new BigDecimal("999.99"), 10);
        electronics.setCategory("electronics");

        ProductRequest clothing = createProductRequest("Shirt", "SKU-SHIRT", new BigDecimal("29.99"), 50);
        clothing.setCategory("clothing");

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(electronics)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clothing)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/products").param("category", "electronics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Phone"));
    }

    private ProductRequest createProductRequest(String name, String sku, BigDecimal price, int stock) {
        ProductRequest request = new ProductRequest();
        request.setName(name);
        request.setSku(sku);
        request.setPrice(price);
        request.setStockQuantity(stock);
        return request;
    }
}
