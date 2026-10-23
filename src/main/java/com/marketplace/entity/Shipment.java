package com.marketplace.entity;

import com.marketplace.common.exception.BusinessException;
import com.marketplace.enums.DeliveryMethod;
import com.marketplace.enums.ShipmentStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * Một đợt giao hàng của một seller cho một order (mỗi cặp order và seller chỉ có một shipment). Gom
 * nguyên các dòng hàng đã CONFIRMED của seller đó. Trạng thái chỉ đổi qua {@link #moveTo}.
 */
@Entity
@Table(name = "shipments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Shipment {

  /** Các bước được phép đi tiếp từ mỗi trạng thái (theo contract shipment); trạng thái khác là điểm cuối. */
  private static final Map<ShipmentStatus, Set<ShipmentStatus>> NEXT = new EnumMap<>(ShipmentStatus.class);

  static {
    NEXT.put(ShipmentStatus.PENDING, Set.of(ShipmentStatus.PREPARING, ShipmentStatus.CANCELLED));
    NEXT.put(ShipmentStatus.PREPARING, Set.of(ShipmentStatus.SHIPPED, ShipmentStatus.CANCELLED));
    NEXT.put(ShipmentStatus.SHIPPED, Set.of(ShipmentStatus.DELIVERED, ShipmentStatus.FAILED));
    NEXT.put(ShipmentStatus.FAILED, Set.of(ShipmentStatus.RETURNED));
  }

  @Id
  @GeneratedValue
  @UuidGenerator
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "delivery_method", nullable = false, length = 30)
  private DeliveryMethod deliveryMethod;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ShipmentStatus status;

  @Column(name = "tracking_code", length = 255)
  private String trackingCode;

  @Column(name = "shipping_fee", nullable = false, precision = 19, scale = 2)
  private BigDecimal shippingFee;

  @Column(name = "shipped_at")
  private Instant shippedAt;

  @Column(name = "delivered_at")
  private Instant deliveredAt;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false)
  private Order order;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "seller_id", nullable = false)
  private User seller;

  // null khi tự giao (SELF_DELIVERY); bắt buộc khi giao qua nền tảng (ràng buộc CHECK ở V16).
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "carrier_id")
  private Carrier carrier;

  @OneToMany(mappedBy = "shipment", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<ShipmentOrderItem> shipmentItems = new ArrayList<>();

  /**
   * Tạo shipment PENDING gom các dòng hàng đã kiểm tra (cùng order, cùng seller, đã CONFIRMED). Phí ship
   * cố định 0.00 như checkout.
   */
  public static Shipment create(
      Order order, User seller, DeliveryMethod method, Carrier carrier, List<OrderItem> items) {
    Shipment shipment = new Shipment();
    shipment.order = order;
    shipment.seller = seller;
    shipment.deliveryMethod = method;
    shipment.carrier = carrier;
    shipment.status = ShipmentStatus.PENDING;
    shipment.shippingFee = BigDecimal.ZERO.setScale(2);
    items.forEach(item -> shipment.shipmentItems.add(ShipmentOrderItem.of(shipment, item)));
    return shipment;
  }

  /**
   * Chuyển sang trạng thái mới nếu được phép, ngược lại 400. SHIPPED bắt buộc có mã vận đơn và ghi lúc
   * gửi; DELIVERED ghi lúc giao. Việc trả kho, huỷ dòng hay hoàn tất order do service làm sau bước này.
   */
  public void moveTo(ShipmentStatus target, String trackingCode, Instant now) {
    if (!NEXT.getOrDefault(status, Set.of()).contains(target)) {
      throw BusinessException.validation("Cannot move a shipment from " + status + " to " + target);
    }
    if (target == ShipmentStatus.SHIPPED) {
      if (trackingCode == null || trackingCode.isBlank()) {
        throw BusinessException.validation("trackingCode is required to mark a shipment SHIPPED");
      }
      this.trackingCode = trackingCode.trim();
      shippedAt = now;
    }
    if (target == ShipmentStatus.DELIVERED) {
      deliveredAt = now;
    }
    status = target;
  }

  public boolean isDelivered() {
    return status == ShipmentStatus.DELIVERED;
  }

  /** Các dòng hàng của shipment này. */
  public List<OrderItem> orderItems() {
    return shipmentItems.stream().map(ShipmentOrderItem::getOrderItem).toList();
  }
}
