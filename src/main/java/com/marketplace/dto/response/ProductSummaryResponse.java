package com.marketplace.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.util.UUID;

/** Một dòng trong danh sách product. Giá và rating được serialize thành chuỗi thập phân theo contract. */
public record ProductSummaryResponse(
    UUID id,
    String name,
    String description,
    String imageUrl,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal price,
    int stock,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal ratingAverage,
    int reviewCount,
    CategorySummaryResponse category,
    UUID sellerId) {}
