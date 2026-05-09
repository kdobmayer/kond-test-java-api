package com.kond.orders.service;

import com.kond.orders.dto.*;
import com.kond.orders.entity.*;
import com.kond.orders.event.OrderStatusChangedEvent;
import com.kond.orders.exception.InvalidStateTransitionException;
import com.kond.orders.exception.ResourceNotFoundException;
import com.kond.orders.repository.CustomerRepository;
import com.kond.orders.repository.OrderRepository;
import com.kond.orders.repository.ProductRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final ApplicationEventPublisher eventPublisher;

    private static final Set<OrderStatus> CANCELLABLE_STATUSES = Set.of(OrderStatus.CREATED, OrderStatus.CONFIRMED);

    public OrderService(OrderRepository orderRepository,
                       CustomerRepository customerRepository,
                       ProductRepository productRepository,
                       InventoryService inventoryService,
                       ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
        this.eventPublisher = eventPublisher;
    }

    public OrderResponse createOrder(CreateOrderRequest request) {
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + request.getCustomerId()));

        Order order = new Order();
        order.setCustomer(customer);
        order.setShippingAddress(request.getShippingAddress());
        order.setNotes(request.getNotes());

        for (CreateOrderRequest.OrderItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + itemReq.getProductId()));

            OrderItem item = new OrderItem();
            item.setProduct(product);
            item.setQuantity(itemReq.getQuantity());
            item.setUnitPrice(product.getPrice());
            order.addItem(item);
        }

        order.recalculateTotal();
        Order saved = orderRepository.save(order);

        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                saved.getId(), null, OrderStatus.CREATED, "Order created"));

        return toResponse(saved);
    }

    public OrderResponse updateStatus(Long orderId, UpdateOrderStatusRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        OrderStatus newStatus;
        try {
            newStatus = OrderStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status: " + request.getStatus());
        }

        validateTransition(order.getStatus(), newStatus);

        OrderStatus previousStatus = order.getStatus();
        order.setStatus(newStatus);

        if (newStatus == OrderStatus.CONFIRMED) {
            for (OrderItem item : order.getItems()) {
                InventoryReserveRequest reserveReq = new InventoryReserveRequest();
                reserveReq.setProductId(item.getProduct().getId());
                reserveReq.setOrderId(order.getId());
                reserveReq.setQuantity(item.getQuantity());
                inventoryService.reserveStock(reserveReq);
            }
        }

        Order saved = orderRepository.save(order);

        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                saved.getId(), previousStatus, newStatus, request.getReason()));

        return toResponse(saved);
    }

    public OrderResponse cancelOrder(Long orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (!CANCELLABLE_STATUSES.contains(order.getStatus())) {
            throw new InvalidStateTransitionException(
                    "Cannot cancel order in status: " + order.getStatus());
        }

        OrderStatus previousStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancellationReason(reason);
        order.setCancelledAt(java.time.LocalDateTime.now());

        if (previousStatus == OrderStatus.CONFIRMED) {
            inventoryService.releaseOrderReservations(orderId);
        }

        Order saved = orderRepository.save(order);

        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                saved.getId(), previousStatus, OrderStatus.CANCELLED, reason));

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByCustomer(Long customerId) {
        return orderRepository.findByCustomerId(customerId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByStatus(OrderStatus status) {
        return orderRepository.findByStatus(status).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private void validateTransition(OrderStatus current, OrderStatus target) {
        boolean valid = switch (current) {
            case CREATED -> target == OrderStatus.CONFIRMED || target == OrderStatus.CANCELLED;
            case CONFIRMED -> target == OrderStatus.SHIPPED || target == OrderStatus.CANCELLED;
            case SHIPPED -> target == OrderStatus.DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };

        if (!valid) {
            throw new InvalidStateTransitionException(
                    "Invalid transition from " + current + " to " + target);
        }
    }

    private OrderResponse toResponse(Order order) {
        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setCustomerId(order.getCustomer().getId());
        response.setCustomerName(order.getCustomer().getFullName());
        response.setStatus(order.getStatus());
        response.setTotalAmount(order.getTotalAmount());
        response.setShippingAddress(order.getShippingAddress());
        response.setNotes(order.getNotes());
        response.setCreatedAt(order.getCreatedAt());
        response.setUpdatedAt(order.getUpdatedAt());

        List<OrderResponse.OrderItemResponse> items = order.getItems().stream()
                .map(item -> {
                    OrderResponse.OrderItemResponse itemResp = new OrderResponse.OrderItemResponse();
                    itemResp.setProductId(item.getProduct().getId());
                    itemResp.setProductName(item.getProduct().getName());
                    itemResp.setQuantity(item.getQuantity());
                    itemResp.setUnitPrice(item.getUnitPrice());
                    itemResp.setSubtotal(item.getSubtotal());
                    return itemResp;
                })
                .collect(Collectors.toList());
        response.setItems(items);

        return response;
    }
}
