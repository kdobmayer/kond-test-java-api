package com.kond.orders.dto;

import jakarta.validation.constraints.NotNull;

public class CreateShipmentRequest {

    @NotNull(message = "Order ID is required")
    private Long orderId;

    private String carrier;
    private String originAddress;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getCarrier() { return carrier; }
    public void setCarrier(String carrier) { this.carrier = carrier; }

    public String getOriginAddress() { return originAddress; }
    public void setOriginAddress(String originAddress) { this.originAddress = originAddress; }
}
