package com.marketplace.service;

import com.marketplace.common.response.PageResponse;
import com.marketplace.dto.request.ConversationCreateRequest;
import com.marketplace.dto.request.MessageSendRequest;
import com.marketplace.dto.response.ConversationResponse;
import com.marketplace.dto.response.MessageResponse;
import com.marketplace.security.AuthUser;
import java.util.List;
import java.util.UUID;

/** Human chat giữa customer và seller theo features/chat/API_CONTRACT.md. */
public interface ChatService {

  /** Kết quả mở hội thoại: {@code created = false} khi cặp customer/seller đã có hội thoại từ trước. */
  record OpenResult(ConversationResponse conversation, boolean created) {}

  /** Các hội thoại người gọi tham gia, có tin mới nhất trước. */
  List<ConversationResponse> listConversations(AuthUser me);

  /** Lấy hội thoại của cặp customer/seller, chưa có thì tạo mới. */
  OpenResult openConversation(AuthUser me, ConversationCreateRequest request);

  /** Lịch sử tin của một hội thoại người gọi tham gia, mới nhất trước. */
  PageResponse<MessageResponse> listMessages(AuthUser me, UUID conversationId, int page, int size);

  /** Gửi tin; gửi lại cùng {@code idempotencyKey} thì trả lại đúng tin đã lưu. */
  MessageResponse sendMessage(
      AuthUser me, UUID conversationId, String idempotencyKey, MessageSendRequest request);

  /** Đánh dấu đã đọc mọi tin người kia gửi trong hội thoại. */
  void markRead(AuthUser me, UUID conversationId);
}
