package com.marketplace.repository;

import com.marketplace.entity.Review;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Đánh giá sản phẩm; mỗi customer tối đa một đánh giá cho mỗi product. */
public interface ReviewRepository extends JpaRepository<Review, UUID> {

  boolean existsByUserIdAndProductId(UUID userId, UUID productId);

  /** Đánh giá của một product; thứ tự do Pageable quyết định (mới nhất trước). */
  Page<Review> findByProductId(UUID productId, Pageable pageable);
}
