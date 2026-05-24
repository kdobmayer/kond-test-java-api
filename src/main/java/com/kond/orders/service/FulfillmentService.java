package com.kond.orders.service;

import com.kond.orders.dto.FulfillmentPlan;
import com.kond.orders.entity.Order;
import com.kond.orders.entity.OrderItem;
import com.kond.orders.entity.OrderStatus;
import com.kond.orders.entity.Product;
import com.kond.orders.exception.InvalidStateTransitionException;
import com.kond.orders.exception.InsufficientStockException;
import com.kond.orders.exception.ResourceNotFoundException;
import com.kond.orders.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class FulfillmentService {

    private final OrderRepository orderRepository;
    private final ShippingRateCalculator shippingRateCalculator;

    public FulfillmentService(OrderRepository orderRepository,
                              ShippingRateCalculator shippingRateCalculator) {
        this.orderRepository = orderRepository;
        this.shippingRateCalculator = shippingRateCalculator;
    }

    public FulfillmentPlan fulfill(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.CREATED) {
            throw new InvalidStateTransitionException(
                    "Cannot create fulfillment plan for order in status: " + order.getStatus());
        }

        List<String> insufficientItems = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            if (product.getAvailableQuantity() < item.getQuantity()) {
                insufficientItems.add(product.getName() +
                        " (available: " + product.getAvailableQuantity() +
                        ", requested: " + item.getQuantity() + ")");
            }
        }
        if (!insufficientItems.isEmpty()) {
            throw new InsufficientStockException(
                    "Insufficient stock for: " + String.join(", ", insufficientItems));
        }

        List<FulfillmentPlan.ReservedItem> reservedItems = new ArrayList<>();
        BigDecimal totalWeight = BigDecimal.ZERO;

        for (OrderItem item : order.getItems()) {
            FulfillmentPlan.ReservedItem reservedItem = new FulfillmentPlan.ReservedItem();
            reservedItem.setProductId(item.getProduct().getId());
            reservedItem.setProductName(item.getProduct().getName());
            reservedItem.setQuantity(item.getQuantity());
            reservedItem.setUnitPrice(item.getUnitPrice());
            reservedItems.add(reservedItem);

            BigDecimal weight = item.getProduct().getWeight() != null
                    ? item.getProduct().getWeight() : BigDecimal.ZERO;
            totalWeight = totalWeight.add(weight.multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        boolean international = isInternational(order.getShippingAddress());
        BigDecimal shippingRate = shippingRateCalculator.calculateSingleRate(totalWeight, "STANDARD", international);

        FulfillmentPlan plan = new FulfillmentPlan();
        plan.setReservedItems(reservedItems);
        plan.setShippingRate(shippingRate);
        plan.setEstimatedDelivery(LocalDateTime.now().plusDays(7));

        return plan;
    }

    private boolean isInternational(String shippingAddress) {
        if (shippingAddress == null || shippingAddress.isBlank()) {
            return false;
        }
        String[] parts = shippingAddress.split(",");
        String destCountry = normalizeCountry(parts[parts.length - 1].trim());
        return !"US".equals(destCountry);
    }

    private String normalizeCountry(String country) {
        if (country == null || country.isBlank()) {
            return "US";
        }

        return switch (country.trim().toUpperCase()) {
            case "USA", "UNITED STATES", "UNITED STATES OF AMERICA" -> "US";
            default -> country.trim().toUpperCase();
        };
    }
}
