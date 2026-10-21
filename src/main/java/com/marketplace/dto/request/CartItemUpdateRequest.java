package com.marketplace.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Body của {@code PATCH /cart/items/{id}}: đặt lại số lượng (không cộng dồn).
 *
 * @param quantity số lượng mới, ít nhất 1; muốn bỏ hẳn thì dùng DELETE
 */
public record CartItemUpdateRequest(@NotNull @Min(1) Integer quantity) {}
