package com.marketplace.enums;

/**
 * Trạng thái xử lý của từng dòng hàng ({@code OrderItem.status}) — do seller xác nhận.
 *
 * <p>Đây là state của quan hệ seller với từng item trong order, không phải tiến độ vận chuyển.
 * Luồng chính theo PLAN là {@code PENDING -> CONFIRMED -> shipment} hoặc
 * {@code PENDING/CONFIRMED -> CANCELLED} tùy business rule. Chỉ seller sở hữu item mới được đổi
 * state; frontend không được tự quyết định transition.
 */
public enum OrderItemStatus {
  /** Item vừa được tạo trong checkout và đang chờ seller xử lý. */
  PENDING,

  /** Seller đã chấp nhận item; item đủ điều kiện để đưa vào shipment của chính seller đó. */
  CONFIRMED,

  /** Item bị huỷ, ví dụ seller từ chối hoặc không còn hàng; cần áp dụng rule hoàn stock nếu có. */
  CANCELLED
}
