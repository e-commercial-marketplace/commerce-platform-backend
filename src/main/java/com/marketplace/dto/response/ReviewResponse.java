package com.marketplace.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Một đánh giá sản phẩm (công khai). */
public record ReviewResponse(
    UUID id, UUID productId, UUID userId, int rating, String comment, Instant createdAt) {}
