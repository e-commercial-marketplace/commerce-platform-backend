package com.marketplace.repository;

import com.marketplace.entity.ShipmentOrderItem;
import com.marketplace.entity.ShipmentOrderItemId;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Dòng hàng nào đã được gán vào shipment (mỗi dòng tối đa một shipment). */
public interface ShipmentOrderItemRepository
    extends JpaRepository<ShipmentOrderItem, ShipmentOrderItemId> {

  boolean existsByOrderItemId(UUID orderItemId);
}
