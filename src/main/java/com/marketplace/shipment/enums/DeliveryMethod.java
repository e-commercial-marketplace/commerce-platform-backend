package com.marketplace.shipment.enums;

/**
 * Hình thức giao hàng của shipment (do seller chọn).
 *
 * <p>Giá trị này mô tả ai cung cấp delivery operation, không phải trạng thái giao hàng. Admin cấu
 * hình option platform; admin không được mô hình hoá thành physical shipper. Seller vẫn chịu
 * ownership của shipment và chỉ được chọn phương thức được business rule cho phép.
 */
public enum DeliveryMethod {
  /** Dùng dịch vụ giao hàng do platform cấu hình và quản lý. */
  PLATFORM_DELIVERY,

  /** Seller tự tổ chức và thực hiện việc giao hàng. */
  SELF_DELIVERY
}
