package com.marketplace.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Một hội thoại human chat (features/chat/API_CONTRACT.md). */
public record ConversationResponse(
    UUID id,
    UUID customerId,
    UUID sellerId,
    UUID productId,
    UUID orderId,
    Instant createdAt,
    Instant lastMessageAt) {}
