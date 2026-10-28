package com.marketplace.repository;

import com.marketplace.entity.Conversation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Hội thoại human chat giữa một customer và một seller. */
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

  /** Hội thoại của đúng cặp customer/seller (mỗi cặp tối đa một hội thoại). */
  Optional<Conversation> findByCustomerIdAndSellerId(UUID customerId, UUID sellerId);

  /**
   * Một hội thoại mà {@code userId} là customer hoặc seller của nó. Người ngoài nhận rỗng, nên service trả
   * 404 giống hệt hội thoại không tồn tại.
   */
  @Query(
      "select c from Conversation c where c.id = :id and (c.customer.id = :userId or c.seller.id = :userId)")
  Optional<Conversation> findByIdForParticipant(@Param("id") UUID id, @Param("userId") UUID userId);

  /**
   * Các hội thoại {@code userId} tham gia: có tin mới nhất trước; hội thoại chưa có tin nằm sau, mới mở
   * trước.
   */
  @Query(
      """
      select c from Conversation c
      where c.customer.id = :userId or c.seller.id = :userId
      order by c.lastMessageAt desc nulls last, c.createdAt desc, c.id
      """)
  List<Conversation> findAllForParticipant(@Param("userId") UUID userId);
}
