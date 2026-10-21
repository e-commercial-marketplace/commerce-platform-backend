package com.marketplace.entity;

import com.marketplace.enums.OrderItemStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/** One seller-owned line in an order, including immutable product name and unit-price snapshots. */
@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

  @Id
  @GeneratedValue
  @UuidGenerator
  private UUID id;

  @Column(name = "product_name", nullable = false, length = 255)
  private String productName;

  @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
  private BigDecimal unitPrice;

  @Column(name = "quantity", nullable = false)
  private int quantity;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private OrderItemStatus status;

  @Column(name = "cancel_reason", columnDefinition = "text")
  private String cancelReason;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false)
  private Order order;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "seller_id", nullable = false)
  private User seller;

  /**
   * Chụp lại product tại thời điểm checkout: tên và giá được copy vào dòng hàng, nên seller đổi giá
   * hay đổi tên sau này không ảnh hưởng đơn cũ. Dòng gắn với seller của product (đơn nhiều seller).
   */
  static OrderItem snapshot(Order order, Product product, int quantity) {
    OrderItem item = new OrderItem();
    item.order = order;
    item.product = product;
    item.seller = product.getSeller();
    item.productName = product.getName();
    item.unitPrice = product.getPrice();
    item.quantity = quantity;
    item.status = OrderItemStatus.PENDING;
    return item;
  }

  /** Thành tiền của dòng theo giá đã chụp. */
  public BigDecimal lineTotal() {
    return unitPrice.multiply(BigDecimal.valueOf(quantity));
  }
}
