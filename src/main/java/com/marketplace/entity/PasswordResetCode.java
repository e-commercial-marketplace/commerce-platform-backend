package com.marketplace.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * Mã đặt lại mật khẩu dùng một lần. DB chỉ giữ hash (BCrypt) của mã 6 chữ số, nên lộ DB cũng không
 * đọc được mã. Một mã dùng được khi: chưa dùng, chưa hết hạn và chưa sai quá số lần cho phép.
 */
@Entity
@Table(name = "password_reset_codes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetCode {

  @Id
  @GeneratedValue
  @UuidGenerator
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "code_hash", nullable = false)
  private String codeHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  // Số lần nhập sai; đủ ngưỡng thì mã không còn dùng được.
  @Column(name = "attempts", nullable = false)
  private int attempts;

  @Column(name = "used_at")
  private Instant usedAt;

  // Đặt từ TimeProvider (không dùng @PrePersist) để giới hạn "60 giây / 5 lần mỗi giờ" dùng chung một đồng hồ.
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  /** Tạo mã mới cho user; {@code codeHash} là hash của mã thô, không bao giờ là mã thô. */
  public static PasswordResetCode issue(User user, String codeHash, Instant now, Instant expiresAt) {
    PasswordResetCode code = new PasswordResetCode();
    code.user = user;
    code.codeHash = codeHash;
    code.createdAt = now;
    code.expiresAt = expiresAt;
    return code;
  }

  /** Còn dùng được: chưa dùng, chưa hết hạn, số lần sai còn dưới {@code maxAttempts}. */
  public boolean isUsable(Instant now, int maxAttempts) {
    return usedAt == null && expiresAt.isAfter(now) && attempts < maxAttempts;
  }

  /** Ghi nhận một lần nhập sai mã. */
  public void recordFailedAttempt() {
    attempts++;
  }

  /** Đánh dấu đã dùng, để mã không dùng lại được lần thứ hai. */
  public void markUsed(Instant now) {
    usedAt = now;
  }
}
