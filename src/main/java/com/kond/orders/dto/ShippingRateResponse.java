package com.kond.orders.dto;

import java.math.BigDecimal;

public class ShippingRateResponse {

    private String carrier;
    private BigDecimal rate;
    private String estimatedDays;
    private String serviceLevel;

    public String getCarrier() { return carrier; }
    public void setCarrier(String carrier) { this.carrier = carrier; }

    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal rate) { this.rate = rate; }

    public String getEstimatedDays() { return estimatedDays; }
    public void setEstimatedDays(String estimatedDays) { this.estimatedDays = estimatedDays; }

    public String getServiceLevel() { return serviceLevel; }
    public void setServiceLevel(String serviceLevel) { this.serviceLevel = serviceLevel; }
}
