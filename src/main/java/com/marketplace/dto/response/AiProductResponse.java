package com.marketplace.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Một product trong câu trả lời của trợ lý AI: chỉ các field công khai cần để hiện thẻ sản phẩm.
 *
 * @param stock số hàng còn lại lúc tìm; {@code 0} là hết hàng (để trả lời "còn hàng không" từ dữ liệu)
 */
public record AiProductResponse(
    UUID id,
    String name,
    String imageUrl,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal price,
    int stock,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal ratingAverage,
    int reviewCount,
    UUID sellerId) {}
