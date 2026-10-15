package com.marketplace;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test cho Phase 0: xác nhận toàn bộ Spring context khởi động được với profile test.
 *
 * <p>Profile test dùng H2 và tắt Flyway để test nhanh, nên test này bắt lỗi wiring/configuration
 * của bean nhưng không chứng minh PostgreSQL migration hoặc endpoint production đã hoạt động.
 */
@SpringBootTest // dựng full application context
@ActiveProfiles("test") // dùng application-test.yml (H2 + tắt Flyway) để test nhanh, không cần Postgres
class MarketplaceApplicationTests {

  /**
   * Nếu context tạo bean thất bại, JUnit sẽ fail ngay trước khi method chạy.
   *
   * <p>Không cần assert thủ công: việc method được gọi nghĩa là Spring Boot đã load xong context.
   */
  @Test
  void contextLoads() {
  }
}
