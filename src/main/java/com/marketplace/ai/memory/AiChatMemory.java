package com.marketplace.ai.memory;

import com.marketplace.common.util.TimeProvider;
import com.marketplace.entity.AiMessage;
import com.marketplace.enums.AiRole;
import com.marketplace.repository.AiChatSessionRepository;
import com.marketplace.repository.AiMessageRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Trí nhớ" của trợ lý AI, cài interface {@link ChatMemory} của Spring AI trên bảng {@code ai_messages}.
 * {@code conversationId} là id của {@code AiChatSession}.
 *
 * <p>Khác {@code MessageWindowChatMemory} có sẵn: bản đó ghi đè cả cuộc trò chuyện bằng N tin cuối (xoá
 * lịch sử cũ). Ở đây chỉ THÊM dòng mới, toàn bộ lịch sử còn nguyên cho {@code GET /ai/sessions/{id}/messages},
 * còn model chỉ được đọc {@link #WINDOW} tin gần nhất để không tốn quá nhiều token.
 */
@Component
@RequiredArgsConstructor
public class AiChatMemory implements ChatMemory {

  /**
   * Số tin gần nhất đưa cho model (khoảng 5 lượt hỏi đáp). Ít để mỗi câu hỏi tốn ít token: gói free của
   * provider thường giới hạn số token mỗi phút. Đổi số này thì đổi cả {@code findTop10...} trong repository.
   */
  public static final int WINDOW = 10;

  private final AiMessageRepository messageRepository;
  private final AiChatSessionRepository sessionRepository;
  private final TimeProvider timeProvider;

  /**
   * Lưu thêm các tin USER và ASSISTANT; bỏ qua tin SYSTEM (prompt hệ thống ghép lại mỗi lần gọi), tin
   * TOOL và tin rỗng. Các tin trong cùng một lần gọi lệch nhau 1 micro giây để thứ tự đọc lại đúng thứ tự
   * thêm (PostgreSQL lưu tới micro giây).
   */
  @Override
  @Transactional
  public void add(String conversationId, List<Message> messages) {
    UUID sessionId = UUID.fromString(conversationId);
    Instant now = timeProvider.now();
    int offset = 0;
    for (Message message : messages) {
      AiRole role = toRole(message.getMessageType());
      String text = message.getText();
      if (role == null || text == null || text.isBlank()) {
        continue;
      }
      messageRepository.save(
          AiMessage.builder()
              .session(sessionRepository.getReferenceById(sessionId))
              .role(role)
              .content(text)
              .createdAt(now.plus(offset++, ChronoUnit.MICROS))
              .build());
    }
  }

  /** {@link #WINDOW} tin gần nhất, cũ trước, dạng tin của Spring AI. */
  @Override
  @Transactional(readOnly = true)
  public List<Message> get(String conversationId) {
    List<AiMessage> newestFirst =
        new ArrayList<>(messageRepository.findTop10BySessionIdOrderByCreatedAtDesc(UUID.fromString(conversationId)));
    Collections.reverse(newestFirst);
    return newestFirst.stream().map(AiChatMemory::toMessage).toList();
  }

  @Override
  @Transactional
  public void clear(String conversationId) {
    messageRepository.deleteBySessionId(UUID.fromString(conversationId));
  }

  private static AiRole toRole(MessageType type) {
    return switch (type) {
      case USER -> AiRole.USER;
      case ASSISTANT -> AiRole.ASSISTANT;
      default -> null; // SYSTEM, TOOL: không lưu
    };
  }

  private static Message toMessage(AiMessage message) {
    return message.getRole() == AiRole.USER
        ? new UserMessage(message.getContent())
        : new AssistantMessage(message.getContent());
  }
}
