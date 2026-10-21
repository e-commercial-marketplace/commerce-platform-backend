package com.marketplace.repository;

import com.marketplace.entity.CartItem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Truy vấn dòng giỏ hàng; mọi truy vấn theo user đều lấy userId từ token, không từ client. */
public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

  /**
   * Cả giỏ của một customer, mới thêm nhất trước ({@code id} làm khoá phụ cho thứ tự ổn định). Nạp sẵn
   * product trong cùng câu query để dựng response không phát sinh một query cho mỗi dòng (N+1).
   */
  @EntityGraph(attributePaths = "product")
  List<CartItem> findByUserIdOrderByCreatedAtDescIdAsc(UUID userId);

  /** Các dòng được chọn khi checkout, chỉ trong giỏ của customer này; nạp sẵn product. */
  @EntityGraph(attributePaths = "product")
  List<CartItem> findByUserIdAndIdIn(UUID userId, Collection<UUID> ids);

  /** Dòng giỏ của customer cho một product (mỗi cặp customer và product chỉ có tối đa một dòng). */
  Optional<CartItem> findByUserIdAndProductId(UUID userId, UUID productId);
}
