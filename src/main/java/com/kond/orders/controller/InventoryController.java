package com.kond.orders.controller;

import com.kond.orders.dto.AvailabilityResponse;
import com.kond.orders.dto.InventoryReserveRequest;
import com.kond.orders.entity.InventoryReservation;
import com.kond.orders.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/reserve")
    public ResponseEntity<Map<String, Object>> reserveStock(@Valid @RequestBody InventoryReserveRequest request) {
        // Duplicated validation — also exists in InventoryService (intentional rough edge)
        if (request.getQuantity() <= 0) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Bad Request",
                    "message", "Quantity must be positive"
            ));
        }

        InventoryReservation reservation = inventoryService.reserveStock(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "reservationId", reservation.getId(),
                "productId", reservation.getProduct().getId(),
                "quantity", reservation.getQuantity(),
                "status", reservation.getStatus().name()
        ));
    }

    @PostMapping("/release/{reservationId}")
    public ResponseEntity<Void> releaseReservation(@PathVariable Long reservationId) {
        inventoryService.releaseReservation(reservationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/availability/{productId}")
    public ResponseEntity<AvailabilityResponse> checkAvailability(@PathVariable Long productId) {
        return ResponseEntity.ok(inventoryService.checkAvailability(productId));
    }
}
