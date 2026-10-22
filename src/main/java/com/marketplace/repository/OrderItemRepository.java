package com.marketplace.repository;

import com.marketplace.entity.OrderItem;
import com.marketplace.enums.OrderItemStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy vấn dòng hàng phía seller; mọi danh sách đều lọc theo sellerId lấy từ token. */
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

  /**
   * Các dòng hàng của một seller. {@code status} và {@code orderId} là bộ lọc tuỳ chọn: truyền
   * {@code null} nghĩa là không lọc theo trường đó. Thứ tự do Pageable quyết định.
   */
  @Query(
      """
      select i from OrderItem i
      where i.seller.id = :sellerId
        and (:status is null or i.status = :status)
        and (:orderId is null or i.order.id = :orderId)
      """)
  Page<OrderItem> findForSeller(
      @Param("sellerId") UUID sellerId,
      @Param("status") OrderItemStatus status,
      @Param("orderId") UUID orderId,
      Pageable pageable);

  /**
   * Đọc và KHOÁ một dòng hàng tới hết transaction. Hai request xác nhận/huỷ cùng một dòng chạy lần lượt,
   * request sau thấy trạng thái mới nhất (ví dụ đã huỷ) nên không trả kho hai lần.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from OrderItem i where i.id = :id")
  Optional<OrderItem> findByIdForUpdate(@Param("id") UUID id);
}
