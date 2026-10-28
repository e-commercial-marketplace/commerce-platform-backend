package com.marketplace.mapper;

import com.marketplace.dto.response.ConversationResponse;
import com.marketplace.dto.response.MessageResponse;
import com.marketplace.entity.Conversation;
import com.marketplace.entity.Message;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Đổi hội thoại và tin nhắn human chat sang response. */
@Mapper(componentModel = "spring")
public interface ChatMapper {

  // Proxy LAZY: đọc id không cần query thêm.
  @Mapping(target = "customerId", source = "customer.id")
  @Mapping(target = "sellerId", source = "seller.id")
  @Mapping(target = "productId", source = "product.id")
  @Mapping(target = "orderId", source = "order.id")
  ConversationResponse toConversation(Conversation conversation);

  @Mapping(target = "conversationId", source = "conversation.id")
  @Mapping(target = "senderId", source = "sender.id")
  @Mapping(target = "isRead", source = "read")
  MessageResponse toMessage(Message message);
}
