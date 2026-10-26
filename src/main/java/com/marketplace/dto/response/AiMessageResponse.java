package com.marketplace.dto.response;

import com.marketplace.enums.AiRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Một tin trong cuộc trò chuyện với trợ lý AI.
 *
 * @param confidence độ tin cậy (tuỳ chọn), hiện luôn {@code null}
 */
public record AiMessageResponse(
    UUID id, UUID sessionId, AiRole role, String content, BigDecimal confidence, Instant createdAt) {}
