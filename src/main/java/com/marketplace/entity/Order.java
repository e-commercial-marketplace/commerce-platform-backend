package com.marketplace.entity;

import com.marketplace.common.exception.BusinessException;
import com.marketplace.enums.OrderStatus;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.UuidGenerator;

/** Customer checkout aggregate; business state transitions belong in the order service. */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

  @Id
  @GeneratedValue
  @UuidGenerator
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private OrderStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "shipping_address", nullable = false, length = 1000)
  private String shippingAddress;

  @Column(name = "shipping_fee", nullable = false, precision = 19, scale = 2)
  private BigDecimal shippingFee;

  @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal discountAmount;

  @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal totalAmount;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User customer;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "voucher_code")
  private Voucher voucher;

  // BatchSize: đọc items/payments của nhiều order bằng một câu IN (...), tránh một query cho mỗi order.
  @BatchSize(size = 50)
  @OneToMany(mappedBy = "order", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
  private List<OrderItem> items = new ArrayList<>();

  @BatchSize(size = 50)
  @OneToMany(mappedBy = "order")
  private List<Payment> payments = new ArrayList<>();

  @BatchSize(size = 50)
  @OneToMany(mappedBy = "order")
  private List<Shipment> shipments = new ArrayList<>();

  @PrePersist
  void onCreate() {
    if (createdAt == null) createdAt = Instant.now();
  }

  /**
   * Mở một order mới ở trạng thái PENDING. Phí ship cố định 0.00; giảm giá 0.00 cho tới khi áp voucher
   * ({@link #applyVoucher}). Tổng tiền tăng dần khi thêm từng dòng hàng.
   */
  public static Order place(User customer, String shippingAddress, Instant now) {
    Order order = new Order();
    order.customer = customer;
    order.shippingAddress = shippingAddress;
    order.createdAt = now;
    order.status = OrderStatus.PENDING;
    order.shippingFee = BigDecimal.ZERO.setScale(2);
    order.discountAmount = BigDecimal.ZERO.setScale(2);
    order.totalAmount = BigDecimal.ZERO.setScale(2);
    return order;
  }

  /** Thêm một dòng hàng chụp lại tên và giá hiện tại của product, rồi tính lại tổng tiền. */
  public OrderItem addItem(Product product, int quantity) {
    OrderItem item = OrderItem.snapshot(this, product, quantity);
    items.add(item);
    recalculateTotal();
    return item;
  }

  /**
   * Gọi sau khi huỷ dòng: tính lại tổng từ các dòng chưa huỷ (bản chụp của dòng đã huỷ giữ nguyên để xem
   * lịch sử). Không còn dòng nào thì huỷ cả order và tổng về 0.00. Sau đó đồng bộ các payment đang chờ:
   * COD nhận tổng mới, VNPAY chuyển FAILED (link cũ mang số tiền cũ); order đã huỷ thì mọi payment đang
   * chờ đều FAILED vì không còn gì để thu.
   */
  public void recalculateAfterCancellation() {
    if (items.stream().allMatch(OrderItem::isCancelled)) {
      status = OrderStatus.CANCELLED;
      discountAmount = BigDecimal.ZERO.setScale(2);
      totalAmount = BigDecimal.ZERO.setScale(2);
    } else {
      recalculateTotal();
    }
    for (Payment payment : payments) {
      payment.updatePendingAmount(totalAmount);
      if (isCancelled()) {
        payment.markFailed();
      }
    }
  }

  /**
   * Huỷ mọi dòng chưa huỷ (ví dụ quá hạn thanh toán trước) và trả về các dòng vừa huỷ, để service cộng
   * trả kho cho đúng các dòng đó (dòng đã huỷ từ trước không bị cộng lần hai).
   */
  public List<OrderItem> cancelRemainingItems(String reason) {
    List<OrderItem> cancelled = items.stream().filter(item -> !item.isCancelled()).toList();
    cancelled.forEach(item -> item.cancel(reason));
    return cancelled;
  }

  /** Thanh toán trước đã hoàn tất: PENDING -> PAID. Trạng thái khác giữ nguyên. */
  public void markPaid() {
    if (status == OrderStatus.PENDING) {
      status = OrderStatus.PAID;
    }
  }

  /** Order trả trước qua VNPAY (có hạn 24 giờ); order COD thì thu tiền khi giao, không có hạn. */
  public boolean isPrepayment() {
    return payments.stream().anyMatch(Payment::isVnpay);
  }

  /**
   * Gọi sau khi một shipment chuyển DELIVERED. Mọi dòng chưa huỷ đều đã nằm trong shipment DELIVERED thì
   * order thành DELIVERED và payment COD đang chờ được ghi nhận đã thu (FR-SHIP-07, FR-PAY-02). Còn dòng
   * chưa giao, hoặc có shipment FAILED/RETURNED, thì order giữ nguyên (FR-SHIP-10).
   */
  public void completeIfAllDelivered(Instant now) {
    if (status != OrderStatus.PENDING && status != OrderStatus.PAID) {
      return;
    }
    Set<UUID> delivered =
        shipments.stream()
            .filter(Shipment::isDelivered)
            .flatMap(shipment -> shipment.orderItems().stream())
            .map(OrderItem::getId)
            .collect(Collectors.toSet());
    boolean allDelivered =
        items.stream()
            .filter(item -> !item.isCancelled())
            .allMatch(item -> delivered.contains(item.getId()));
    if (allDelivered) {
      status = OrderStatus.DELIVERED;
      // VNPAY đã COMPLETED từ lúc trả tiền; chỉ COD còn chờ thu.
      payments.stream().filter(payment -> !payment.isVnpay()).forEach(payment -> payment.complete(now));
    }
  }

  /** Khách xác nhận đã nhận hàng: DELIVERED -> COMPLETED. Trạng thái khác thì 400. */
  public void markReceived() {
    if (status != OrderStatus.DELIVERED) {
      throw BusinessException.validation("Only a DELIVERED order can be confirmed as received");
    }
    status = OrderStatus.COMPLETED;
  }

  public boolean isPending() {
    return status == OrderStatus.PENDING;
  }

  public boolean isPaid() {
    return status == OrderStatus.PAID;
  }

  public boolean isCancelled() {
    return status == OrderStatus.CANCELLED;
  }

  /** Áp voucher (đã được trừ lượt dùng) và tính lại giảm giá, tổng tiền. Mỗi order tối đa một voucher. */
  public void applyVoucher(Voucher voucher) {
    this.voucher = voucher;
    recalculateTotal();
  }

  /**
   * Tạm tính = cộng (giá x số lượng) các dòng chưa huỷ. Giảm giá = % của voucher trên tạm tính, làm tròn 2
   * chữ số; tính lại mỗi lần để huỷ bớt dòng không làm tổng âm. Tổng = tạm tính - giảm giá + phí ship.
   */
  private void recalculateTotal() {
    BigDecimal subtotal =
        items.stream()
            .filter(item -> !item.isCancelled())
            .map(OrderItem::lineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    discountAmount =
        voucher == null
            ? BigDecimal.ZERO.setScale(2)
            : subtotal
                .multiply(BigDecimal.valueOf(voucher.getDiscountPercent()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    totalAmount = subtotal.subtract(discountAmount).add(shippingFee);
  }
}
