package com.marketplace.common.util;

import jakarta.servlet.http.HttpServletRequest;

/** Lấy IP của người gọi (VNPAY bắt buộc gửi kèm vnp_IpAddr). */
public final class ClientIp {

  private ClientIp() {}

  /**
   * Khi chạy sau proxy (Render), IP thật nằm ở phần đầu header {@code X-Forwarded-For}; chạy local thì
   * không có header này nên dùng địa chỉ kết nối.
   */
  public static String of(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
