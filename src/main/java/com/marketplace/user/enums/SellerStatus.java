package com.marketplace.user.enums;

/**
 * Trạng thái duyệt seller (cột {@code users.seller_status}, chỉ áp dụng khi role là {@code SELLER}).
 *
 * <p>Role xác định loại tài khoản, còn status xác định seller có được phép thực hiện operation bán
 * hàng hay không. Auth/service phải kiểm tra cả hai; enum tự nó không chặn request. Luồng quản trị
 * thường là {@code PENDING -> APPROVED} hoặc {@code REJECTED/SUSPENDED}.
 */
public enum SellerStatus {
  /** Seller vừa đăng ký và đang chờ admin xét duyệt. */
  PENDING,

  /** Seller được phép bán và xử lý resource theo ownership. */
  APPROVED,

  /** Hồ sơ seller bị từ chối; không được thực hiện nghiệp vụ bán hàng. */
  REJECTED,

  /** Seller tạm thời bị khoá; cần policy riêng để mở lại. */
  SUSPENDED
}
