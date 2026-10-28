package com.marketplace.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * Một tin nhắn human chat. {@code isRead} khai báo tên JSON rõ ràng: Jackson sẽ bỏ chữ "is" của
 * component boolean nếu để mặc định.
 */
public record MessageResponse(
    UUID id,
    UUID conversationId,
    UUID senderId,
    String content,
    String imageUrl,
    @JsonProperty("isRead") boolean isRead,
    Instant createdAt) {}
