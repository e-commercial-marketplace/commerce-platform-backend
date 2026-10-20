package com.marketplace.enums;

/**
 * Vai trò người dùng trong marketplace (lưu ở cột {@code users.role}).
 *
 * <p>Role là coarse-grained authorization; service vẫn phải kiểm tra ownership và trạng thái
 * resource. Ví dụ role {@code SELLER} không cho phép seller sửa product/order item/shipment của
 * seller khác. Không nhận role từ request body để quyết định quyền; lấy từ authenticated principal.
 */
public enum Role {
  /** Người mua: cart, checkout/order, review và chat theo ownership. */
  CUSTOMER,

  /** Người bán: quản lý product/stock và fulfillment của resource do mình sở hữu. */
  SELLER,

  /** Quản trị: các thao tác admin như category, voucher, moderation hoặc refund approval. */
  ADMIN
}
