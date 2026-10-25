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
import org.hibernate.annotations.UuidGenerator;

/**
 * Đánh giá của customer cho một product (mỗi customer tối đa một đánh giá cho mỗi product, có ràng buộc
 * unique ở DB). Điều kiện được đánh giá (order COMPLETED chứa product) do ReviewService kiểm.
 */
@Entity
@Table(name = "reviews", uniqueConstraints = @UniqueConstraint(
    name = "uq_reviews_user_product", columnNames = {"user_id", "product_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {

  @Id
  @GeneratedValue
  @UuidGenerator
  private UUID id;

  @Column(name = "rating", nullable = false)
  private int rating;

  @Column(name = "comment", nullable = false, columnDefinition = "text")
  private String comment;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  public static Review create(User user, Product product, int rating, String comment, Instant now) {
    Review review = new Review();
    review.user = user;
    review.product = product;
    review.rating = rating;
    review.comment = comment;
    review.createdAt = now;
    return review;
  }
}
