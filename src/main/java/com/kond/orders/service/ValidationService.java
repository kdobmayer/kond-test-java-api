package com.kond.orders.service;

import com.kond.orders.dto.InventoryReserveRequest;
import com.kond.orders.entity.Product;
import com.kond.orders.exception.InsufficientStockException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ValidationService {

    public void validateReservationRequest(InventoryReserveRequest request) {
        validateReservationQuantity(request.getQuantity());
    }

    public void validateReservationQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }

    public void validateAvailableStock(Product product, int requestedQuantity) {
        int available = product.getStockQuantity() - product.getReservedQuantity();
        if (available < requestedQuantity) {
            throw new InsufficientStockException(
                    "Insufficient stock for product " + product.getName() +
                    ". Available: " + available + ", Requested: " + requestedQuantity);
        }
    }

    public void validateProductStockUpdate(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Stock quantity cannot be negative");
        }
    }
}
