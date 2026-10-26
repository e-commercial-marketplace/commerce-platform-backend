package com.marketplace.repository;

import com.marketplace.entity.AiChatSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Cuộc trò chuyện với trợ lý AI; luôn lọc theo chủ sở hữu lấy từ token. */
public interface AiChatSessionRepository extends JpaRepository<AiChatSession, UUID> {

  /** Session của đúng customer này; của người khác thì rỗng như không tồn tại. */
  Optional<AiChatSession> findByIdAndUserId(UUID id, UUID userId);

  /** Các session của customer, mới nhất trước; id làm khoá phụ cho thứ tự ổn định. */
  List<AiChatSession> findByUserIdOrderByCreatedAtDescIdAsc(UUID userId);
}
