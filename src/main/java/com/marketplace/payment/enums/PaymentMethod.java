package com.marketplace.payment.enums;

/**
 * Phương thức thanh toán của một lần thử ({@code Payment.method}).
 *
 * <p>Mỗi payment là một attempt gắn với order. Với VNPAY, retry phải tạo record Payment mới thay
 * vì ghi đè attempt thất bại; điều này giữ audit trail và hỗ trợ callback idempotency theo PLAN.
 */
public enum PaymentMethod {
  /** Cash on delivery: tạo payment {@code PENDING}, hoàn tất khi quy tắc thu COD được đáp ứng. */
  COD,

  /** VNPAY: tạo payment {@code PENDING}, redirect người dùng rồi xử lý callback. */
  VNPAY
}
