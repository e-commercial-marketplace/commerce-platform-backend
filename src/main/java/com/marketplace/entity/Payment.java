package com.marketplace.entity;

import com.marketplace.enums.PaymentMethod;
import com.marketplace.enums.PaymentStatus;
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
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/** One payment attempt for an order; retaining failed attempts allows safe retries and audit. */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

  @Id
  @GeneratedValue
  @UuidGenerator
  private UUID id;

  @Column(name = "amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(name = "method", nullable = false, length = 20)
  private PaymentMethod method;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private PaymentStatus status;

  @Column(name = "transaction_id", length = 255, unique = true)
  private String transactionId;

  @Column(name = "paid_at")
  private Instant paidAt;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false)
  private Order order;

  /** Lần thanh toán COD chờ thu tiền khi giao, số tiền bằng tổng của order lúc checkout. */
  public static Payment pendingCod(Order order) {
    Payment payment = new Payment();
    payment.order = order;
    payment.method = PaymentMethod.COD;
    payment.status = PaymentStatus.PENDING;
    payment.amount = order.getTotalAmount();
    return payment;
  }

  /**
   * Một lần thử VNPAY đang chờ khách trả tiền, số tiền bằng tổng hiện tại của order.
   *
   * @param transactionId mã tham chiếu gửi VNPAY (vnp_TxnRef), duy nhất cho mỗi lần thử
   */
  public static Payment pendingVnpay(Order order, String transactionId) {
    Payment payment = new Payment();
    payment.order = order;
    payment.method = PaymentMethod.VNPAY;
    payment.status = PaymentStatus.PENDING;
    payment.amount = order.getTotalAmount();
    payment.transactionId = transactionId;
    return payment;
  }

  public boolean isPending() {
    return status == PaymentStatus.PENDING;
  }

  public boolean isVnpay() {
    return method == PaymentMethod.VNPAY;
  }

  public boolean isCompleted() {
    return status == PaymentStatus.COMPLETED;
  }

  /**
   * Order đổi tổng tiền (một dòng bị huỷ) khi payment còn chờ. COD: nhận số tiền mới, vì thu tiền lúc
   * giao. VNPAY: link thanh toán đã gửi mang số tiền cũ, nên lần thử này hết dùng được (FAILED); khách
   * thanh toán lại với số tiền mới.
   */
  public void updatePendingAmount(BigDecimal newAmount) {
    if (!isPending()) {
      return;
    }
    if (isVnpay()) {
      status = PaymentStatus.FAILED;
    } else {
      amount = newAmount;
    }
  }

  /** VNPAY xác nhận đã trả tiền. Chỉ đi được từ PENDING; service kiểm tra trước khi gọi. */
  public void complete(Instant now) {
    if (isPending()) {
      status = PaymentStatus.COMPLETED;
      paidAt = now;
    }
  }

  /** Không còn thu được (ví dụ cả order đã huỷ): payment đang chờ kết thúc ở FAILED. */
  public void markFailed() {
    if (isPending()) {
      status = PaymentStatus.FAILED;
    }
  }
}
