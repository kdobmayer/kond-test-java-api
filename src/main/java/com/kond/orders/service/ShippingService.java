package com.kond.orders.service;

import com.kond.orders.dto.CreateShipmentRequest;
import com.kond.orders.dto.ShipmentResponse;
import com.kond.orders.entity.Order;
import com.kond.orders.entity.OrderStatus;
import com.kond.orders.entity.Shipment;
import com.kond.orders.entity.ShipmentStatus;
import com.kond.orders.exception.InvalidStateTransitionException;
import com.kond.orders.exception.ResourceNotFoundException;
import com.kond.orders.repository.OrderRepository;
import com.kond.orders.repository.ShipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class ShippingService {

    private final ShipmentRepository shipmentRepository;
    private final OrderRepository orderRepository;
    private final ShippingRateCalculator rateCalculator;

    public ShippingService(ShipmentRepository shipmentRepository,
                          OrderRepository orderRepository,
                          ShippingRateCalculator rateCalculator) {
        this.shipmentRepository = shipmentRepository;
        this.orderRepository = orderRepository;
        this.rateCalculator = rateCalculator;
    }

    public ShipmentResponse createShipment(CreateShipmentRequest request) {
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + request.getOrderId()));

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new InvalidStateTransitionException(
                    "Cannot create shipment for order in status: " + order.getStatus());
        }

        String carrier = request.getCarrier() != null ? request.getCarrier() : "Standard Post";
        BigDecimal shippingCost = rateCalculator.calculateSingleRate(
                new BigDecimal("1.0"), "STANDARD", false);

        Shipment shipment = new Shipment();
        shipment.setOrder(order);
        shipment.setTrackingNumber(generateTrackingNumber());
        shipment.setCarrier(carrier);
        shipment.setShippingCost(shippingCost);
        shipment.setOriginAddress(request.getOriginAddress());
        shipment.setDestinationAddress(order.getShippingAddress());
        shipment.setEstimatedDelivery(LocalDateTime.now().plusDays(7));

        Shipment saved = shipmentRepository.save(shipment);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ShipmentResponse trackShipment(String trackingNumber) {
        Shipment shipment = shipmentRepository.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found: " + trackingNumber));
        return toResponse(shipment);
    }

    @Transactional(readOnly = true)
    public ShipmentResponse getShipmentByOrder(Long orderId) {
        Shipment shipment = shipmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("No shipment for order: " + orderId));
        return toResponse(shipment);
    }

    public ShipmentResponse updateShipmentStatus(Long shipmentId, ShipmentStatus newStatus) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found: " + shipmentId));

        shipment.setStatus(newStatus);
        if (newStatus == ShipmentStatus.PICKED_UP || newStatus == ShipmentStatus.IN_TRANSIT) {
            shipment.setShippedAt(LocalDateTime.now());
        }
        if (newStatus == ShipmentStatus.DELIVERED) {
            shipment.setDeliveredAt(LocalDateTime.now());
        }

        Shipment saved = shipmentRepository.save(shipment);
        return toResponse(saved);
    }

    private String generateTrackingNumber() {
        return "TRK-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    private ShipmentResponse toResponse(Shipment shipment) {
        ShipmentResponse response = new ShipmentResponse();
        response.setId(shipment.getId());
        response.setOrderId(shipment.getOrder().getId());
        response.setTrackingNumber(shipment.getTrackingNumber());
        response.setCarrier(shipment.getCarrier());
        response.setStatus(shipment.getStatus());
        response.setShippingCost(shipment.getShippingCost());
        response.setOriginAddress(shipment.getOriginAddress());
        response.setDestinationAddress(shipment.getDestinationAddress());
        response.setShippedAt(shipment.getShippedAt());
        response.setEstimatedDelivery(shipment.getEstimatedDelivery());
        response.setCreatedAt(shipment.getCreatedAt());
        return response;
    }
}
