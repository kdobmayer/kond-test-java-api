package com.kond.orders.controller;

import com.kond.orders.dto.*;
import com.kond.orders.entity.ShipmentStatus;
import com.kond.orders.service.ShippingRateCalculator;
import com.kond.orders.service.ShippingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/shipping")
public class ShippingController {

    private final ShippingService shippingService;
    private final ShippingRateCalculator rateCalculator;

    public ShippingController(ShippingService shippingService, ShippingRateCalculator rateCalculator) {
        this.shippingService = shippingService;
        this.rateCalculator = rateCalculator;
    }

    @PostMapping("/rates")
    public ResponseEntity<List<ShippingRateResponse>> calculateRates(@Valid @RequestBody ShippingRateRequest request) {
        List<ShippingRateResponse> rates = rateCalculator.calculateRates(request);
        return ResponseEntity.ok(rates);
    }

    @PostMapping("/shipments")
    public ResponseEntity<ShipmentResponse> createShipment(@Valid @RequestBody CreateShipmentRequest request) {
        ShipmentResponse response = shippingService.createShipment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/track/{trackingNumber}")
    public ResponseEntity<ShipmentResponse> trackShipment(@PathVariable String trackingNumber) {
        return ResponseEntity.ok(shippingService.trackShipment(trackingNumber));
    }

    @GetMapping("/shipments/order/{orderId}")
    public ResponseEntity<ShipmentResponse> getShipmentByOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(shippingService.getShipmentByOrder(orderId));
    }

    @PutMapping("/shipments/{id}/status")
    public ResponseEntity<ShipmentResponse> updateShipmentStatus(@PathVariable Long id,
                                                                  @RequestBody Map<String, String> body) {
        ShipmentStatus status = parseShipmentStatus(body);
        return ResponseEntity.ok(shippingService.updateShipmentStatus(id, status));
    }

    private ShipmentStatus parseShipmentStatus(Map<String, String> body) {
        if (body == null) {
            throw new IllegalArgumentException("Status is required");
        }

        String statusValue = body.get("status");
        if (statusValue == null || statusValue.isBlank()) {
            throw new IllegalArgumentException("Status is required");
        }

        try {
            return ShipmentStatus.valueOf(statusValue.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid shipment status: " + statusValue);
        }
    }
}
