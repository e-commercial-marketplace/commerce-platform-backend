package com.marketplace.entity;

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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.UuidGenerator;

/** Customer checkout aggregate; business state transitions belong in the order service. */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
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
  @Builder.Default
  private List<OrderItem> items = new ArrayList<>();

  @BatchSize(size = 50)
  @OneToMany(mappedBy = "order")
  @Builder.Default
  private List<Payment> payments = new ArrayList<>();

  @BatchSize(size = 50)
  @OneToMany(mappedBy = "order")
  @Builder.Default
  private List<Shipment> shipments = new ArrayList<>();

  @PrePersist
  void onCreate() {
    if (createdAt == null) createdAt = Instant.now();
  }
}
