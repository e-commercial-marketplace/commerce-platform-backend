package com.marketplace.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller chỉ tồn tại trong test, dùng để chứng minh prefix {@code /api/v1} do {@code WebConfig}
 * thêm vào REST controller.
 *
 * <p>Class nằm trong test sources nên không được đóng gói vào artifact; Spring quét nó trong context
 * test. Endpoint đặt dưới {@code /products/...} để khớp rule permitAll của {@code SecurityConfig},
 * nhờ vậy test gọi được qua HTTP mà không cần token.
 */
@RestController
class PrefixProbeController {

  /**
   * Route khai báo KHÔNG kèm prefix; URL thật là {@code /api/v1/products/prefix-probe}.
   *
   * @return chuỗi cố định để test khẳng định request đã tới đúng handler
   */
  @GetMapping("/products/prefix-probe")
  String probe() {
    return "prefix-probe-ok";
  }
}
