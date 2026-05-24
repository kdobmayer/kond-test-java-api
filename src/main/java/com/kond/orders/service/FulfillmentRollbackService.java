package com.kond.orders.service;

import com.kond.orders.entity.ShipmentStatus;
import com.kond.orders.repository.ShipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FulfillmentRollbackService {

    private final InventoryService inventoryService;
    private final ShipmentRepository shipmentRepository;
    private final ShippingService shippingService;

    public FulfillmentRollbackService(InventoryService inventoryService,
                                      ShipmentRepository shipmentRepository,
                                      ShippingService shippingService) {
        this.inventoryService = inventoryService;
        this.shipmentRepository = shipmentRepository;
        this.shippingService = shippingService;
    }

    public void rollbackFulfillment(Long orderId) {
        inventoryService.releaseOrderReservations(orderId);

        shipmentRepository.findByOrderId(orderId).ifPresent(shipment -> {
            if (shipment.getStatus() != ShipmentStatus.CANCELLED
                    && shipment.getStatus() != ShipmentStatus.DELIVERED
                    && shipment.getStatus() != ShipmentStatus.RETURNED) {
                shippingService.updateShipmentStatus(shipment.getId(), ShipmentStatus.CANCELLED);
            }
        });
    }
}
