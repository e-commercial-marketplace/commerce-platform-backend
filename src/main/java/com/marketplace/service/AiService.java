package com.marketplace.service;

import com.marketplace.dto.request.AiAskRequest;
import com.marketplace.dto.response.AiChatSessionResponse;
import com.marketplace.dto.response.AiMessageResponse;
import com.marketplace.dto.response.AiProductSearchResponse;
import com.marketplace.security.AuthUser;
import java.util.List;
import java.util.UUID;

/** Trợ lý AI theo features/ai/API_CONTRACT.md. */
public interface AiService {

  /**
   * Trả lời một câu hỏi. Dùng LLM khi được; vượt 20 câu/phút, chưa cấu hình provider hoặc provider lỗi
   * thì trả lời bằng luật. Customer có session và chat memory; guest không lưu gì.
   *
   * @param caller người gọi lấy từ token; {@code null} với guest
   * @param clientIp IP để đếm hạn mức của guest
   */
  AiProductSearchResponse ask(AuthUser caller, AiAskRequest request, String clientIp);

  /** Các session của customer, mới nhất trước. */
  List<AiChatSessionResponse> listSessions(UUID customerId);

  /** Lịch sử một session của customer, cũ nhất trước; không có hoặc của người khác thì 404. */
  List<AiMessageResponse> listMessages(UUID customerId, UUID sessionId);
}
