package com.marketplace.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;

/**
 * Lần tìm product đã thực sự chạy (sau khi chuẩn hoá), không phải lời model kể lại. Mọi field có thể
 * {@code null}.
 *
 * @param sort một trong {@code relevance, price_asc, price_desc, rating_desc}
 */
public record AiProductQueryResponse(
    String text,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal minPrice,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal maxPrice,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal minRating,
    String sort) {}
