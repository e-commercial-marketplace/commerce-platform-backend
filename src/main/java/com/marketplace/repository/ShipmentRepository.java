package com.marketplace.repository;

import com.marketplace.entity.Shipment;
import com.marketplace.enums.ShipmentStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Shipment của seller; mọi truy vấn của seller đều lọc theo sellerId lấy từ token. */
public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {

  /** Shipment của seller, lọc tuỳ chọn theo trạng thái và order (tham số null nghĩa là không lọc). */
  @Query(
      """
      select s from Shipment s
      where s.seller.id = :sellerId
        and (:status is null or s.status = :status)
        and (:orderId is null or s.order.id = :orderId)
      """)
  Page<Shipment> findForSeller(
      @Param("sellerId") UUID sellerId,
      @Param("status") ShipmentStatus status,
      @Param("orderId") UUID orderId,
      Pageable pageable);

  /** Đọc và KHOÁ shipment tới hết transaction, để hai lần đổi trạng thái chạy lần lượt. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from Shipment s where s.id = :id")
  Optional<Shipment> findByIdForUpdate(@Param("id") UUID id);

  /** Mỗi seller chỉ có một shipment cho mỗi order (ràng buộc unique từ V13). */
  boolean existsByOrderIdAndSellerId(UUID orderId, UUID sellerId);
}
