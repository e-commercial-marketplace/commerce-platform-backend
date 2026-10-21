package com.marketplace.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * Một dòng trong giỏ hàng: customer nào chọn product nào, bao nhiêu cái. Không lưu giá: giá luôn đọc
 * từ product hiện tại, checkout mới chụp lại giá vào OrderItem.
 */
@Entity
@Table(name = "cart_items", uniqueConstraints = @UniqueConstraint(
    name = "uq_cart_items_user_product", columnNames = {"user_id", "product_id"}))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem {

  @Id
  @GeneratedValue
  @UuidGenerator
  private UUID id;

  @Column(name = "quantity", nullable = false)
  private int quantity;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  // Thời điểm thêm lần đầu; giỏ sắp mới nhất trước. Cộng dồn số lượng không đổi giá trị này.
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  /** Tạo dòng giỏ mới cho customer. */
  public static CartItem add(User user, Product product, int quantity, Instant now) {
    CartItem item = new CartItem();
    item.user = user;
    item.product = product;
    item.quantity = quantity;
    item.createdAt = now;
    return item;
  }
}
