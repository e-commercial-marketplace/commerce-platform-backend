package com.marketplace.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Body (tuỳ chọn) của {@code PATCH /seller/order-items/{id}/cancel}.
 *
 * @param cancelReason lý do huỷ hiển thị cho khách, có thể bỏ trống
 */
public record OrderItemCancelRequest(@Size(max = 1000) String cancelReason) {}
