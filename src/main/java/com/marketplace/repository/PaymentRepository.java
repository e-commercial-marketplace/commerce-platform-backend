package com.marketplace.repository;

import com.marketplace.entity.Payment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Lưu các lần thanh toán của order (COD chờ thu, các lần thử VNPAY). */
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

  /**
   * Id order của lần thử VNPAY có mã tham chiếu vnp_TxnRef này. Chỉ lấy id (không nạp payment) để service
   * KHOÁ order trước rồi mới đọc payment, nhờ vậy luôn đọc được trạng thái payment mới nhất.
   */
  @Query("select p.order.id from Payment p where p.transactionId = :transactionId")
  Optional<UUID> findOrderIdByTransactionId(@Param("transactionId") String transactionId);
}
