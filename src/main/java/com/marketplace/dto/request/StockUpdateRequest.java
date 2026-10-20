package com.marketplace.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Body của {@code PATCH /seller/products/{id}/stock}: tồn kho bắt buộc và không âm. */
public record StockUpdateRequest(@NotNull @Min(0) Integer stock) {}
