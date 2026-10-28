package com.marketplace.repository;

import com.marketplace.entity.Message;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tin nhắn human chat. */
public interface MessageRepository extends JpaRepository<Message, UUID> {

  /** Lịch sử của một hội thoại; thứ tự do Pageable quyết định (mới nhất trước). */
  Page<Message> findByConversationId(UUID conversationId, Pageable pageable);

  /** Tin người gửi đã lưu với Idempotency-Key này (để gửi lại không tạo bản trùng). */
  Optional<Message> findBySenderIdAndClientMessageId(UUID senderId, String clientMessageId);

  /**
   * Đánh dấu đã đọc mọi tin chưa đọc mà NGƯỜI KHÁC gửi trong hội thoại. Trả số tin vừa đổi (0 nếu không
   * có gì để đổi).
   */
  @Modifying
  @Query(
      """
      update Message m set m.read = true
      where m.conversation.id = :conversationId and m.sender.id <> :readerId and m.read = false
      """)
  int markReadByRecipient(@Param("conversationId") UUID conversationId, @Param("readerId") UUID readerId);
}
