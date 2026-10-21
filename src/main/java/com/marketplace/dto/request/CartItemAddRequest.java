package com.marketplace.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Body của {@code POST /cart/items}. Product đã có trong giỏ thì số lượng được cộng dồn.
 *
 * @param quantity số lượng thêm vào, ít nhất 1
 */
public record CartItemAddRequest(@NotNull UUID productId, @NotNull @Min(1) Integer quantity) {}
