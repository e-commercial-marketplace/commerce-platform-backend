package com.marketplace.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Một dòng trong danh sách product. Giá và rating được serialize thành chuỗi thập phân theo contract.
 *
 * @param active {@code true} nếu đang bán; danh sách công khai luôn là {@code true}, danh sách của
 *     seller có thể là {@code false} với product đã ẩn
 */
public record ProductSummaryResponse(
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
