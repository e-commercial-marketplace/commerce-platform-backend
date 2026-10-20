package com.marketplace.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.util.UUID;

/** Chi tiết một product; cùng trường với {@link ProductSummaryResponse} theo contract. */
public record ProductResponse(
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
