package com.kond.orders.repository;

import com.kond.orders.entity.InventoryReservation;
import com.kond.orders.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {
    List<InventoryReservation> findByOrderIdAndStatus(Long orderId, ReservationStatus status);
    List<InventoryReservation> findByProductIdAndStatus(Long productId, ReservationStatus status);
}
