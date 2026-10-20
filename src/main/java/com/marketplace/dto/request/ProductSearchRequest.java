package com.marketplace.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Tham số tìm kiếm product công khai; mọi bộ lọc đều tuỳ chọn.
 *
 * @param q từ khoá tìm trong tên và mô tả
 * @param minPrice giá tối thiểu (bao gồm)
 * @param maxPrice giá tối đa (bao gồm)
 * @param minRating rating trung bình tối thiểu, 0 đến 5
 * @param sort một trong {@code relevance, price_asc, price_desc, rating_desc}
 * @param page chỉ số trang, bắt đầu từ 0
 */
public record ProductSearchRequest(
    String q,
    UUID categoryId,
    BigDecimal minPrice,
    BigDecimal maxPrice,
    BigDecimal minRating,
    String sort,
    int page,
    int size) {}
