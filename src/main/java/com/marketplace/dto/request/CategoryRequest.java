package com.marketplace.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body của {@code POST} và {@code PATCH /admin/categories}: tên bắt buộc, không để trống. */
public record CategoryRequest(@NotBlank @Size(max = 120) String name) {}
