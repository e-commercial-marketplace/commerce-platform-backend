package com.marketplace.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Một cuộc trò chuyện với trợ lý AI của customer. */
public record AiChatSessionResponse(UUID id, UUID userId, Instant createdAt) {}
