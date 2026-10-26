package com.marketplace.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.util.UUID;

/** Một product trong câu trả lời của trợ lý AI: chỉ các field công khai cần để hiện thẻ sản phẩm. */
public record AiProductResponse(
    UUID id,
    String name,
    String imageUrl,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal price,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal ratingAverage,
    int reviewCount,
    UUID sellerId) {}
