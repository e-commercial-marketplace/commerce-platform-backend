package com.marketplace.repository;

import com.marketplace.entity.Product;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository
    extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

  /**
   * Tìm theo specification và nạp sẵn category, seller trong cùng câu query để mapper đọc được mà
   * không phát sinh thêm query cho từng dòng (N+1).
   */
  @Override
  @EntityGraph(attributePaths = {"category", "seller"})
  Page<Product> findAll(Specification<Product> spec, Pageable pageable);

  @EntityGraph(attributePaths = {"category", "seller"})
  Optional<Product> findByIdAndActiveTrue(UUID id);

  /** Category còn product nào không (kể cả product đã ẩn), dùng để chặn xoá category. */
  boolean existsByCategoryId(UUID categoryId);

  /** Số product đang bán của một seller, dùng cho trang shop công khai. */
  long countBySellerIdAndActiveTrue(UUID sellerId);

  /**
   * Trừ tồn kho có điều kiện trong MỘT câu UPDATE: chỉ trừ khi product còn bán và còn đủ hàng. Trả về
   * số dòng bị đổi: 1 là trừ thành công, 0 là không đủ hàng (hoặc vừa bị ẩn). Hai checkout tranh nhau
   * món cuối cùng: database chỉ cho một bên trừ được, bên kia nhận 0, nên không bao giờ bán quá kho.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      "update Product p set p.stock = p.stock - :quantity"
          + " where p.id = :id and p.active = true and p.stock >= :quantity")
  int decreaseStock(@Param("id") UUID id, @Param("quantity") int quantity);

  /**
   * Cộng một đánh giá vào điểm trung bình và số lượt đánh giá trong MỘT câu UPDATE: vế phải dùng giá trị cũ
   * của dòng, và dòng bị khoá khi cập nhật, nên hai đánh giá cùng lúc không làm sai số đếm. Cột
   * rating_average là NUMERIC(3,2) nên DB tự làm tròn 2 chữ số.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      "update Product p set p.ratingAverage = (p.ratingAverage * p.reviewCount + :rating)"
          + " / (p.reviewCount + 1), p.reviewCount = p.reviewCount + 1 where p.id = :id")
  int addRating(@Param("id") UUID id, @Param("rating") int rating);

  /** Cộng lại tồn kho khi một dòng hàng bị huỷ (không cần điều kiện: cộng thêm luôn hợp lệ). */
  @Modifying(flushAutomatically = true)
  @Query("update Product p set p.stock = p.stock + :quantity where p.id = :id")
  int increaseStock(@Param("id") UUID id, @Param("quantity") int quantity);

  /** Id các category đang có ít nhất một product đang bán (một câu query cho mọi category). */
  @Query("select distinct p.category.id from Product p where p.active = true")
  Set<UUID> findCategoryIdsWithActiveProducts();
}
