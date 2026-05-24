package com.kond.orders.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kond.orders.dto.InventoryReserveRequest;
import com.kond.orders.entity.Product;
import com.kond.orders.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class InventoryLockingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InventoryService inventoryService;

    @Test
    void reserveStock_optimisticLock_returns409FromGlobalHandler() throws Exception {
        when(inventoryService.reserveStock(any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Product.class, 1L));

        InventoryReserveRequest request = buildRequest(1L, 1L, 5);

        mockMvc.perform(post("/api/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Concurrent modification detected. Please retry the request."));

        verify(inventoryService, times(1)).reserveStock(any());
    }

    @Test
    void releaseReservation_optimisticLock_returns409FromGlobalHandler() throws Exception {
        doThrow(new ObjectOptimisticLockingFailureException(Product.class, 1L))
                .when(inventoryService).releaseReservation(anyLong());

        mockMvc.perform(post("/api/inventory/release/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Concurrent modification detected. Please retry the request."));

        verify(inventoryService, times(1)).releaseReservation(1L);
    }

    private InventoryReserveRequest buildRequest(Long productId, Long orderId, int quantity) {
        InventoryReserveRequest request = new InventoryReserveRequest();
        request.setProductId(productId);
        request.setOrderId(orderId);
        request.setQuantity(quantity);
        return request;
    }

}
