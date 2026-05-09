package com.kond.orders.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    @EventListener
    public void handleOrderStatusChanged(OrderStatusChangedEvent event) {
        log.info("Order {} status changed: {} -> {} (reason: {})",
                event.getOrderId(),
                event.getPreviousStatus(),
                event.getNewStatus(),
                event.getReason());
    }
}
