package com.marketplace.repository;

import com.marketplace.entity.AiMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Các tin của cuộc trò chuyện với trợ lý AI. */
public interface AiMessageRepository extends JpaRepository<AiMessage, UUID> {

  /** Toàn bộ lịch sử, cũ nhất trước (để hiển thị). */
  List<AiMessage> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

  /** 20 tin mới nhất, mới nhất trước; chat memory đảo lại để đưa cho model theo thứ tự thời gian. */
  List<AiMessage> findTop20BySessionIdOrderByCreatedAtDesc(UUID sessionId);

  void deleteBySessionId(UUID sessionId);
}
