package com.marketplace.shipment.enums;

/**
 * Tiến độ giao hàng ({@code Shipment.status}) — theo PLAN Phase 7.
 *
 * <p>Luồng chính là {@code PENDING -> PREPARING -> SHIPPED -> DELIVERED}; giao thất bại chuyển
 * shipment sang {@code FAILED}, sau đó chỉ chuyển sang {@code RETURNED} khi kiện hàng đã về kho
 * seller. {@code FAILED} không tự hoàn tồn kho; service chỉ hoàn số lượng của shipment sau khi
 * transition sang {@code RETURNED} thành công. {@code PENDING/PREPARING -> CANCELLED} theo rule.
 * Khi vào {@code SHIPPED}, service phải kiểm tra {@code trackingCode} và ghi {@code shippedAt}; khi
 * vào {@code DELIVERED}, phải ghi {@code deliveredAt}. Chỉ khi mọi shipment bắt buộc đã delivered
 * thì order mới được cập nhật thành {@code OrderStatus.DELIVERED}.
 */
public enum ShipmentStatus {
  /** Shipment được tạo từ các order item hợp lệ nhưng seller chưa chuẩn bị hàng. */
  PENDING,

  /** Seller đang đóng gói/chuẩn bị shipment. */
  PREPARING,

  /** Đã bàn giao cho đơn vị giao hoặc bắt đầu tự giao; phải có tracking và shipped timestamp. */
  SHIPPED,

  /** Người nhận đã nhận hàng; phải có delivered timestamp. */
  DELIVERED,

  /** Giao hàng thất bại; cần quy trình xử lý riêng, không tự suy ra order đã cancelled. */
  FAILED,

  /** Kiện hàng đã trả về kho seller; đây mới là mốc cho phép hoàn lại số lượng đã xuất kho. */
  RETURNED,

  /** Shipment bị huỷ khi còn ở trạng thái cho phép huỷ. */
  CANCELLED
}
