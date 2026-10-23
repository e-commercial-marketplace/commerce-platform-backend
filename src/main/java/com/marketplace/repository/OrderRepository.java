package com.marketplace.repository;

import com.marketplace.entity.Order;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Truy vấn order; mọi truy vấn của customer đều lọc theo customerId lấy từ token. */
public interface OrderRepository extends JpaRepository<Order, UUID> {

  /** Các order của một customer; thứ tự do Pageable quyết định (mới nhất trước). */
  Page<Order> findByCustomerId(UUID customerId, Pageable pageable);

  /**
   * Một order của đúng customer này. Order của người khác cũng ra rỗng, nên controller trả 404 giống
   * hệt order không tồn tại (không lộ việc id đó có thật). Nạp sẵn các dòng hàng.
   */
  @EntityGraph(attributePaths = "items")
  Optional<Order> findByIdAndCustomerId(UUID id, UUID customerId);

  /**
   * Đọc và KHOÁ order tới hết transaction. Hai seller huỷ hai dòng của cùng một order sẽ chạy lần lượt,
   * nên mỗi lần tính lại tổng đều dựa trên trạng thái mới nhất.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from Order o where o.id = :id")
  Optional<Order> findByIdForUpdate(@Param("id") UUID id);

  /**
   * Id các order trả trước (có lần thử VNPAY) vẫn PENDING, tạo trước {@code deadline} và chưa có payment
   * COMPLETED: đây là các order quá hạn 24 giờ cần huỷ. Chỉ lấy id; mỗi order được khoá và kiểm lại
   * trong transaction riêng lúc huỷ.
   */
  @Query(
      "select o.id from Order o where o.status = com.marketplace.enums.OrderStatus.PENDING"
          + " and o.createdAt < :deadline"
          + " and exists (select p from Payment p where p.order = o"
          + "   and p.method = com.marketplace.enums.PaymentMethod.VNPAY)"
          + " and not exists (select p from Payment p where p.order = o"
          + "   and p.status = com.marketplace.enums.PaymentStatus.COMPLETED)")
  List<UUID> findExpiredPrepaymentOrderIds(@Param("deadline") Instant deadline);

  /**
   * Tự hoàn tất các order DELIVERED mà không shipment nào được giao từ {@code cutoff} trở về sau (tức lần
   * giao cuối đã quá hạn khách xác nhận). Một câu UPDATE duy nhất nên an toàn khi chạy song song với khách
   * bấm xác nhận: cả hai chỉ đổi DELIVERED thành COMPLETED.
   *
   * @return số order vừa được hoàn tất
   */
  @Transactional
  @Modifying
  @Query(
      "update Order o set o.status = com.marketplace.enums.OrderStatus.COMPLETED"
          + " where o.status = com.marketplace.enums.OrderStatus.DELIVERED"
          + " and not exists (select s from Shipment s where s.order = o and s.deliveredAt >= :cutoff)")
  int completeDeliveredBefore(@Param("cutoff") Instant cutoff);
}
