package com.kond.orders.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class FulfillmentPlan {

    private List<ReservedItem> reservedItems;
    private BigDecimal shippingRate;
    private LocalDateTime estimatedDelivery;

    public static class ReservedItem {
        private Long productId;
        private String productName;
        private int quantity;
        private BigDecimal unitPrice;

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }

        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }

        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }

        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    }

    public List<ReservedItem> getReservedItems() { return reservedItems; }
    public void setReservedItems(List<ReservedItem> reservedItems) { this.reservedItems = reservedItems; }

    public BigDecimal getShippingRate() { return shippingRate; }
    public void setShippingRate(BigDecimal shippingRate) { this.shippingRate = shippingRate; }

    public LocalDateTime getEstimatedDelivery() { return estimatedDelivery; }
    public void setEstimatedDelivery(LocalDateTime estimatedDelivery) { this.estimatedDelivery = estimatedDelivery; }
}
