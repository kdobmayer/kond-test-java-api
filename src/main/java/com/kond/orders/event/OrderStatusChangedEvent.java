package com.kond.orders.event;

import com.kond.orders.entity.OrderStatus;

public class OrderStatusChangedEvent {

    private final Long orderId;
    private final OrderStatus previousStatus;
    private final OrderStatus newStatus;
    private final String reason;

    public OrderStatusChangedEvent(Long orderId, OrderStatus previousStatus, OrderStatus newStatus, String reason) {
        this.orderId = orderId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.reason = reason;
    }

    public Long getOrderId() { return orderId; }
    public OrderStatus getPreviousStatus() { return previousStatus; }
    public OrderStatus getNewStatus() { return newStatus; }
    public String getReason() { return reason; }
}
