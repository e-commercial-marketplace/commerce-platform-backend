package com.marketplace.dto.response;

import com.marketplace.enums.AiAnswerSource;
import java.util.List;
import java.util.UUID;

/**
 * Câu trả lời của {@code POST /ai/ask}.
 *
 * @param query lần tìm product đã chạy; {@code null} khi câu hỏi không cần tìm
 * @param products tối đa 5 product lấy từ catalog thật; rỗng khi không tìm
 * @param sessionId session của customer (mới tạo hoặc đã gửi lên); {@code null} với guest
 * @param answeredBy {@code AI} hay {@code RULES} (trả lời không cần LLM)
 */
public record AiProductSearchResponse(
    String answer,
    AiProductQueryResponse query,
    List<AiProductResponse> products,
    UUID sessionId,
    AiAnswerSource answeredBy) {}
