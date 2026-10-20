package com.marketplace.common.util;

import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Bean tiện ích dùng chung cho thời gian.
 *
 * <p>Service/domain code nên gọi bean này thay vì rải {@code Instant.now()} khắp nơi. Cách tập
 * trung này giữ timestamp ở UTC và giúp sau này thay clock tại một điểm khi checkout, payment
 * callback, shipment hoặc chat được triển khai.
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
}
