package com.marketplace.common.util;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Bean tiện ích dùng chung cho thời gian và idempotency key.
 *
 * <p>Service/domain code nên gọi bean này thay vì rải {@code Instant.now()} hoặc
 * {@code UUID.randomUUID()} khắp nơi. Cách tập trung này giữ timestamp ở UTC và giúp sau này thay
 * clock/key strategy tại một điểm khi checkout, payment callback, shipment hoặc chat được triển
 * khai.
 *
 * <p>{@code TimeProvider} hiện dùng system UTC trực tiếp. {@code shortcut: chưa có constructor
 * injection cho Clock, thêm injection khi cần test thời gian xác định hoặc chạy backfill theo clock
 * giả.}
 */
@Component
public class TimeProvider {

  /** Clock hệ thống UTC để mọi timestamp backend có cùng timezone chuẩn. */
  private final Clock clock = Clock.systemUTC();

  /**
   * Lấy thời điểm hiện tại theo UTC.
   *
   * <p>Dùng cho các field như {@code createdAt}, {@code updatedAt}, {@code shippedAt} và
   * {@code deliveredAt}. Không dùng kết quả này làm business decision nếu transaction còn phải
   * kiểm tra lại dữ liệu sau đó.
   *
   * @return thời điểm hiện tại theo UTC
   */
  public Instant now() {
    return Instant.now(clock);
  }

  /**
   * Tạo UUID dạng text để làm correlation/idempotency key.
   *
   * <p>Key chỉ giúp nhận diện một lần xử lý; payment service hoặc message service vẫn phải lưu key
   * và kiểm tra unique/idempotency trong transaction. Chỉ tạo key mới khi bắt đầu một operation mới,
   * không tạo lại khi retry cùng operation.
   *
   * @return UUID mới, ví dụ dùng cho VNPAY reference hoặc message retry
   */
  public String newIdempotencyKey() {
    return UUID.randomUUID().toString();
  }
}
