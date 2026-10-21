package com.marketplace.repository;

import com.marketplace.entity.PasswordResetCode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy vấn mã đặt lại mật khẩu theo user. */
public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, UUID> {

  /** Mã chưa dùng mới nhất của user (mã mới luôn thay mã cũ, nên chỉ mã này có giá trị). */
  Optional<PasswordResetCode> findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(UUID userId);

  /** Mã mới nhất của user (kể cả đã dùng), để áp giới hạn "60 giây mới được xin mã mới". */
  Optional<PasswordResetCode> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

  /** Số mã đã cấp cho user từ thời điểm {@code since}, để áp giới hạn số mã mỗi giờ. */
  long countByUserIdAndCreatedAtAfter(UUID userId, Instant since);

  /** Vô hiệu mọi mã chưa dùng của user (khi cấp mã mới hoặc khi đặt lại mật khẩu thành công). */
  @Modifying
  @Query(
      "update PasswordResetCode c set c.usedAt = :now"
          + " where c.user.id = :userId and c.usedAt is null")
  int invalidateAll(@Param("userId") UUID userId, @Param("now") Instant now);
}
