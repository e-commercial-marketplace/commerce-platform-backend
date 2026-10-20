package com.marketplace.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Body của {@code POST /seller/products}. Quy tắc validate lấy từ contract catalog:
 * {@code price > 0}, {@code stock >= 0}, bắt buộc có tên và category.
 *
 * @param price giá tối đa 2 chữ số thập phân (khớp cột numeric(19,2))
 * @param active bỏ trống nghĩa là đang bán ({@code true})
 */
public record ProductCreateRequest(
    @NotBlank @Size(max = 255) String name,
    String description,
    @Size(max = 2048) String imageUrl,
    // inclusive = false nghĩa là phải LỚN HƠN 0, giá bằng 0 bị từ chối.
    @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 17, fraction = 2)
        BigDecimal price,
    @NotNull @Min(0) Integer stock,
    @NotNull UUID categoryId,
    Boolean active) {}
