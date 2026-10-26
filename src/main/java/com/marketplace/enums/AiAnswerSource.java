package com.marketplace.enums;

/** Ai đã viết câu trả lời của {@code POST /ai/ask} (field {@code answeredBy}). */
public enum AiAnswerSource {
  /** LLM trả lời (có thể đã gọi tool tìm product). */
  AI,
  /** Backend tự trả lời không cần LLM: vượt hạn mức, chưa cấu hình provider hoặc provider lỗi. */
  RULES
}
