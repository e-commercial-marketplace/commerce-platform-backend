package com.marketplace.enums;

/**
 * Trạng thái thanh toán ({@code Payment.status}) của từng lần thử.
 *
 * <p>{@code PENDING} chờ callback/thu COD; {@code COMPLETED} hoặc {@code FAILED} là kết quả cuối
 * của attempt; {@code REFUNDED} chỉ dùng sau khi đã hoàn tiền theo quy trình admin. Không xoá hoặc
 * tái sử dụng record thất bại khi retry VNPAY: tạo Payment mới và giữ reference cũ để audit.
 */
public enum PaymentStatus {
  /** Payment vừa tạo, chưa có kết quả cuối. */
  PENDING,

  /** Provider/thu COD xác nhận payment thành công. */
  COMPLETED,

  /** Attempt thất bại; order vẫn còn để có thể retry theo business rule. */
  FAILED,

  /** Số tiền của payment đã hoàn lại theo quy trình được phép. */
  REFUNDED
}
