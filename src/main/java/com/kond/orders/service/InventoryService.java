package com.kond.orders.service;

import com.kond.orders.dto.AvailabilityResponse;
import com.kond.orders.dto.InventoryReserveRequest;
import com.kond.orders.entity.*;
import com.kond.orders.exception.InsufficientStockException;
import com.kond.orders.exception.ResourceNotFoundException;
import com.kond.orders.repository.InventoryReservationRepository;
import com.kond.orders.repository.OrderRepository;
import com.kond.orders.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class InventoryService {

    private final ProductRepository productRepository;
    private final InventoryReservationRepository reservationRepository;
    private final OrderRepository orderRepository;

    public InventoryService(ProductRepository productRepository,
                           InventoryReservationRepository reservationRepository,
                           OrderRepository orderRepository) {
        this.productRepository = productRepository;
        this.reservationRepository = reservationRepository;
        this.orderRepository = orderRepository;
    }

    // INTENTIONAL: No optimistic locking — race condition possible under concurrent access
    public InventoryReservation reserveStock(InventoryReserveRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.getProductId()));

        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + request.getOrderId()));

        // Duplicated validation (also in InventoryController) — intentional rough edge
        if (request.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        int available = product.getStockQuantity() - product.getReservedQuantity();
        if (available < request.getQuantity()) {
            throw new InsufficientStockException(
                    "Insufficient stock for product " + product.getName() +
                    ". Available: " + available + ", Requested: " + request.getQuantity());
        }

        // No locking here — concurrent requests can both pass the check above
        product.setReservedQuantity(product.getReservedQuantity() + request.getQuantity());
        productRepository.save(product);

        InventoryReservation reservation = new InventoryReservation();
        reservation.setProduct(product);
        reservation.setOrder(order);
        reservation.setQuantity(request.getQuantity());
        reservation.setStatus(ReservationStatus.ACTIVE);

        return reservationRepository.save(reservation);
    }

    public void releaseReservation(Long reservationId) {
        InventoryReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));

        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new IllegalArgumentException("Reservation is not active");
        }

        Product product = reservation.getProduct();
        product.setReservedQuantity(product.getReservedQuantity() - reservation.getQuantity());
        productRepository.save(product);

        reservation.setStatus(ReservationStatus.RELEASED);
        reservation.setReleasedAt(LocalDateTime.now());
        reservationRepository.save(reservation);
    }

    public void releaseOrderReservations(Long orderId) {
        List<InventoryReservation> reservations = reservationRepository
                .findByOrderIdAndStatus(orderId, ReservationStatus.ACTIVE);

        for (InventoryReservation reservation : reservations) {
            Product product = reservation.getProduct();
            product.setReservedQuantity(product.getReservedQuantity() - reservation.getQuantity());
            productRepository.save(product);

            reservation.setStatus(ReservationStatus.RELEASED);
            reservation.setReleasedAt(LocalDateTime.now());
            reservationRepository.save(reservation);
        }
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse checkAvailability(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        AvailabilityResponse response = new AvailabilityResponse();
        response.setProductId(product.getId());
        response.setProductName(product.getName());
        response.setStockQuantity(product.getStockQuantity());
        response.setReservedQuantity(product.getReservedQuantity());
        response.setAvailableQuantity(product.getAvailableQuantity());
        response.setAvailable(product.getAvailableQuantity() > 0);
        return response;
    }
}
