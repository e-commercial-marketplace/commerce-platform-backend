package com.marketplace.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body của {@code POST /products/{productId}/reviews}. Người viết lấy từ token, không nhận từ client.
 *
 * @param rating từ 1 đến 5 (FR-REV-03)
 * @param comment bắt buộc, không chỉ có khoảng trắng, tối đa 2000 ký tự
 */
public record ReviewCreateRequest(
    @NotNull @Min(1) @Max(5) Integer rating, @NotBlank @Size(max = 2000) String comment) {}
