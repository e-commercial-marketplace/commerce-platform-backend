package com.marketplace.enums;

/**
 * Trạng thái đơn hàng ({@code Order.status}) ở mức tổng thể.
 *
 * <p>Order status không thay thế {@code OrderItemStatus} hoặc {@code ShipmentStatus}. Một order có
 * thể chứa item của nhiều seller, vì vậy service chỉ đặt {@code DELIVERED} sau khi mọi shipment bắt
 * buộc đã giao. Các transition phải được thực hiện trong service/transaction, không đổi enum trực
 * tiếp từ request mobile. {@code FAILED} và {@code RETURNED} là kết quả giao hàng theo từng seller
 * ở {@code ShipmentStatus}, không phải trạng thái tổng hợp của order.
 */
public enum OrderStatus {
  /** Checkout đã tạo order nhưng payment chưa hoàn tất. */
  PENDING,

  /** Payment rule đã xác nhận order được thanh toán. */
  PAID,

  /** Tất cả shipment cần thiết của order đã chuyển sang {@code DELIVERED}. */
  DELIVERED,

  /** Khách đã xác nhận nhận hàng, hoặc hệ thống tự xác nhận sau 3 ngày kể từ lần giao cuối. */
  COMPLETED,

  /** Order đã bị huỷ theo business rule; không đồng nghĩa mọi shipment đều bị huỷ tự động. */
  CANCELLED
}
