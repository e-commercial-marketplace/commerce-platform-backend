package com.marketplace.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body của {@code POST /admin/carriers}: thêm một đơn vị vận chuyển (đang hoạt động). */
public record CarrierCreateRequest(@NotBlank @Size(max = 100) String name) {}
