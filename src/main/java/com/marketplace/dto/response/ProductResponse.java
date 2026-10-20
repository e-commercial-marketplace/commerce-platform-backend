package com.marketplace.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Chi tiết một product; cùng trường với {@link ProductSummaryResponse} theo contract.
 *
 * @param active {@code true} nếu đang bán; endpoint công khai chỉ trả product {@code active = true},
 *     còn endpoint của seller trả cả product đã ẩn nên cần trường này để phân biệt
 */
public record ProductResponse(
    UUID id,
    String name,
    String description,
    String imageUrl,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal price,
    int stock,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal ratingAverage,
    int reviewCount,
    boolean active,
    CategorySummaryResponse category,
    UUID sellerId) {}
