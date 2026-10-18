package com.marketplace.catalog.entity;

import com.marketplace.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/** Seller-owned catalog listing; its price is live and not an order-history snapshot. */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

  @Id
  @GeneratedValue
  @UuidGenerator
  private UUID id;

  @Column(name = "name", nullable = false, length = 255)
  private String name;

  @Column(name = "description", columnDefinition = "text")
  private String description;

  @Column(name = "image_url", length = 2048)
  private String imageUrl;

  @Column(name = "price", nullable = false, precision = 19, scale = 2)
  private BigDecimal price;

  @Column(name = "stock", nullable = false)
  private int stock;

  @Column(name = "rating_average", nullable = false, precision = 3, scale = 2)
  private BigDecimal ratingAverage = BigDecimal.ZERO;

  @Column(name = "review_count", nullable = false)
  private int reviewCount;

  @Column(name = "active", nullable = false)
  private boolean active;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "category_id", nullable = false)
  private Category category;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "seller_id", nullable = false)
  private User seller;
}
