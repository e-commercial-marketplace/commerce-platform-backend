package com.marketplace.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Whole-line mapping; partial quantities and split shipments are not modeled in this MVP.
 * Shipment/order and seller equality span rows and must be verified by ShipmentService on write.
 */
@Entity
@Table(name = "shipment_order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShipmentOrderItem {

  // @MapsId: Hibernate tự điền hai phần của khoá từ shipment và orderItem lúc lưu.
  @EmbeddedId
  private ShipmentOrderItemId id = new ShipmentOrderItemId();

  @MapsId("shipmentId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "shipment_id", nullable = false)
  private Shipment shipment;

  @MapsId("orderItemId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_item_id", nullable = false, unique = true)
  private OrderItem orderItem;

  /** Gán nguyên một dòng hàng vào shipment. */
  static ShipmentOrderItem of(Shipment shipment, OrderItem orderItem) {
    ShipmentOrderItem mapping = new ShipmentOrderItem();
    mapping.shipment = shipment;
    mapping.orderItem = orderItem;
    return mapping;
  }
}
