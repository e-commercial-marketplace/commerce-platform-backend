package com.marketplace.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Body của {@code POST /ai/ask}.
 *
 * @param message câu hỏi, bắt buộc, tối đa 1000 ký tự (giới hạn để mỗi câu không tốn quá nhiều token)
 * @param sessionId customer: {@code null} để mở session mới, hoặc một session của chính mình; guest: phải
 *     là {@code null}
 */
public record AiAskRequest(@NotBlank @Size(max = 1000) String message, UUID sessionId) {}
