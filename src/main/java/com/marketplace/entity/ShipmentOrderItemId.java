package com.marketplace.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Composite primary key for the shipment/order-item association row. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class ShipmentOrderItemId implements Serializable {

  @Column(name = "shipment_id", nullable = false)
  private UUID shipmentId;

  @Column(name = "order_item_id", nullable = false)
  private UUID orderItemId;
}
