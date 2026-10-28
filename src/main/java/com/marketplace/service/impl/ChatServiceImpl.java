package com.marketplace.service.impl;

import com.marketplace.common.exception.BusinessException;
import com.marketplace.common.response.PageResponse;
import com.marketplace.common.util.Pages;
import com.marketplace.common.util.TimeProvider;
import com.marketplace.dto.request.ConversationCreateRequest;
import com.marketplace.dto.request.MessageSendRequest;
import com.marketplace.dto.response.ConversationResponse;
import com.marketplace.dto.response.MessageResponse;
import com.marketplace.entity.Conversation;
import com.marketplace.entity.Message;
import com.marketplace.entity.Order;
import com.marketplace.entity.Product;
import com.marketplace.entity.User;
import com.marketplace.enums.Role;
import com.marketplace.enums.SellerStatus;
import com.marketplace.mapper.ChatMapper;
import com.marketplace.repository.ConversationRepository;
import com.marketplace.repository.MessageRepository;
import com.marketplace.repository.OrderRepository;
import com.marketplace.repository.ProductRepository;
import com.marketplace.repository.UserRepository;
import com.marketplace.security.AuthUser;
import com.marketplace.service.ChatService;
import com.marketplace.websocket.ChatPush;
import com.marketplace.websocket.MessageCreatedEvent;
import com.marketplace.websocket.MessagesReadEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Human chat (features/chat/API_CONTRACT.md). Mọi luật nằm ở đây: ai được mở hội thoại với ai, chỉ người
 * trong hội thoại mới đọc/gửi được, chống gửi trùng bằng Idempotency-Key, đánh dấu đã đọc.
 *
 * <p>Đẩy WebSocket không làm ở đây: service chỉ phát {@link ChatPush}; listener gửi đi sau khi transaction
 * commit, nên người nhận không bao giờ thấy một tin mà DB rồi lại quay lui.
 */
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

  /** Độ dài tối đa của Idempotency-Key (cột client_message_id, V19). */
  static final int MAX_KEY_LENGTH = 100;

  private final ConversationRepository conversationRepository;
  private final MessageRepository messageRepository;
  private final UserRepository userRepository;
  private final ProductRepository productRepository;
  private final OrderRepository orderRepository;
  private final ChatMapper chatMapper;
  private final TimeProvider timeProvider;
  private final ApplicationEventPublisher eventPublisher;
  private final TransactionTemplate transactionTemplate;

  @Override
  @Transactional(readOnly = true)
  public List<ConversationResponse> listConversations(AuthUser me) {
    return conversationRepository.findAllForParticipant(me.id()).stream()
        .map(chatMapper::toConversation)
        .toList();
  }

  /**
   * Cố ý không có {@code @Transactional} trên cả method: phần tạo mới chạy trong transaction riêng, để khi
   * hai request mở cùng một cặp chạy song song, request thua (vướng unique customer/seller) vẫn đọc lại
   * được hội thoại request kia vừa tạo.
   */
  @Override
  public OpenResult openConversation(AuthUser me, ConversationCreateRequest request) {
    // Bước 1: xác định cặp customer/seller. Phía người gọi lấy từ token, phía còn lại lấy từ body.
    UUID customerId;
    UUID sellerId;
    if (me.role() == Role.CUSTOMER) {
      customerId = me.id();
      sellerId = requireApprovedSeller(request.sellerId());
    } else {
      sellerId = me.id();
      customerId = requireCustomer(request.customerId());
    }

    // Bước 2: cặp này đã có hội thoại thì trả lại luôn (giữ nguyên ngữ cảnh cũ).
    Optional<Conversation> existing = conversationRepository.findByCustomerIdAndSellerId(customerId, sellerId);
    if (existing.isPresent()) {
      return new OpenResult(chatMapper.toConversation(existing.get()), false);
    }

    // Bước 3: tạo mới trong transaction riêng.
    try {
      Conversation created =
          transactionTemplate.execute(status -> createConversation(customerId, sellerId, request));
      return new OpenResult(chatMapper.toConversation(created), true);
    } catch (DataIntegrityViolationException e) {
      // Một request khác vừa tạo đúng cặp này trước: dùng hội thoại của nó.
      Conversation winner =
          conversationRepository.findByCustomerIdAndSellerId(customerId, sellerId).orElseThrow(() -> e);
      return new OpenResult(chatMapper.toConversation(winner), false);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public PageResponse<MessageResponse> listMessages(AuthUser me, UUID conversationId, int page, int size) {
    findForParticipant(conversationId, me.id());
    return PageResponse.from(
        messageRepository.findByConversationId(conversationId, Pages.newestFirst(page, size)),
        chatMapper::toMessage);
  }

  /**
   * Không có {@code @Transactional} trên cả method, cùng lý do với {@link #openConversation}: hai request
   * cùng Idempotency-Key chạy song song thì request thua đọc lại tin request kia đã lưu.
   */
  @Override
  public MessageResponse sendMessage(
      AuthUser me, UUID conversationId, String idempotencyKey, MessageSendRequest request) {
    // Bước 1: kiểm tra dữ liệu vào.
    String key = idempotencyKey == null ? "" : idempotencyKey.trim();
    if (key.isEmpty() || key.length() > MAX_KEY_LENGTH) {
      throw BusinessException.validation("Idempotency-Key header is required (1 to 100 characters)");
    }
    String content = request.content() == null ? "" : request.content().trim();
    String imageUrl = request.imageUrl() == null || request.imageUrl().isBlank() ? null : request.imageUrl().trim();
    if (content.isEmpty() && imageUrl == null) {
      throw BusinessException.validation("A message needs content or an imageUrl");
    }

    // Bước 2: key này đã gửi rồi thì trả lại đúng tin cũ, không tạo tin mới, không đẩy sự kiện mới.
    Optional<Message> sameKey = messageRepository.findBySenderIdAndClientMessageId(me.id(), key);
    if (sameKey.isPresent()) {
      return toResendResult(sameKey.get(), conversationId);
    }

    // Bước 3: lưu tin mới trong transaction riêng.
    try {
      return transactionTemplate.execute(
          status -> saveMessage(me.id(), conversationId, key, content, imageUrl));
    } catch (DataIntegrityViolationException e) {
      // Request cùng key chạy song song đã lưu trước.
      Message winner = messageRepository.findBySenderIdAndClientMessageId(me.id(), key).orElseThrow(() -> e);
      return toResendResult(winner, conversationId);
    }
  }

  @Override
  @Transactional
  public void markRead(AuthUser me, UUID conversationId) {
    Conversation conversation = findForParticipant(conversationId, me.id());
    int changed = messageRepository.markReadByRecipient(conversationId, me.id());
    // Chỉ báo cho người kia khi thật sự có tin vừa chuyển sang đã đọc.
    if (changed > 0) {
      UUID otherId = otherParticipantId(conversation, me.id());
      eventPublisher.publishEvent(
          new ChatPush(Set.of(otherId), new MessagesReadEvent(conversationId, me.id())));
    }
  }

  // ---------- phần dùng chung ----------

  /** Chạy bên trong transaction của {@link #openConversation}. */
  private Conversation createConversation(UUID customerId, UUID sellerId, ConversationCreateRequest request) {
    Product product = null;
    if (request.productId() != null) {
      product =
          productRepository
              .findById(request.productId())
              .orElseThrow(() -> BusinessException.notFound("Product", request.productId()));
    }
    Order order = null;
    if (request.orderId() != null) {
      // Đơn của customer khác cũng ra rỗng → 404, không lộ đơn có tồn tại hay không.
      order =
          orderRepository
              .findByIdAndCustomerId(request.orderId(), customerId)
              .orElseThrow(() -> BusinessException.notFound("Order", request.orderId()));
    }
    Conversation conversation =
        Conversation.builder()
            .customer(userRepository.getReferenceById(customerId))
            .seller(userRepository.getReferenceById(sellerId))
            .product(product)
            .order(order)
            .createdAt(timeProvider.now())
            .build();
    // saveAndFlush: ghi xuống DB ngay, để lỗi trùng cặp (nếu có) nổ ra trong try của openConversation.
    return conversationRepository.saveAndFlush(conversation);
  }

  /** Chạy bên trong transaction của {@link #sendMessage}. */
  private MessageResponse saveMessage(
      UUID senderId, UUID conversationId, String key, String content, String imageUrl) {
    Conversation conversation = findForParticipant(conversationId, senderId);
    Instant now = timeProvider.now();
    Message message =
        messageRepository.saveAndFlush(
            Message.builder()
                .conversation(conversation)
                .sender(userRepository.getReferenceById(senderId))
                .content(content)
                .imageUrl(imageUrl)
                .read(false)
                .clientMessageId(key)
                .createdAt(now)
                .build());
    conversation.setLastMessageAt(now);

    MessageResponse response = chatMapper.toMessage(message);
    // Cả hai người đều nhận: người nhận thấy tin mới, các thiết bị khác của người gửi cũng cập nhật.
    eventPublisher.publishEvent(
        new ChatPush(
            Set.of(conversation.getCustomer().getId(), conversation.getSeller().getId()),
            new MessageCreatedEvent(response)));
    return response;
  }

  /** Tin đã lưu với cùng key: chỉ trả lại khi nó thuộc đúng hội thoại đang gửi. */
  private MessageResponse toResendResult(Message stored, UUID conversationId) {
    if (!stored.getConversation().getId().equals(conversationId)) {
      throw BusinessException.validation("This Idempotency-Key was already used for another conversation");
    }
    return chatMapper.toMessage(stored);
  }

  /** Hội thoại mà {@code userId} tham gia; người ngoài nhận 404 giống như hội thoại không tồn tại. */
  private Conversation findForParticipant(UUID conversationId, UUID userId) {
    return conversationRepository
        .findByIdForParticipant(conversationId, userId)
        .orElseThrow(() -> BusinessException.notFound("Conversation", conversationId));
  }

  private static UUID otherParticipantId(Conversation conversation, UUID userId) {
    UUID customerId = conversation.getCustomer().getId();
    return customerId.equals(userId) ? conversation.getSeller().getId() : customerId;
  }

  /** Customer chỉ được mở hội thoại với seller đã APPROVED; mọi trường hợp khác là 404 giống nhau. */
  private UUID requireApprovedSeller(UUID sellerId) {
    if (sellerId == null) {
      throw BusinessException.validation("sellerId is required");
    }
    User seller =
        userRepository
            .findById(sellerId)
            .filter(u -> u.getRole() == Role.SELLER && u.getSellerStatus() == SellerStatus.APPROVED)
            .orElseThrow(() -> BusinessException.notFound("Seller", sellerId));
    return seller.getId();
  }

  /** Seller chỉ được mở hội thoại với một CUSTOMER. */
  private UUID requireCustomer(UUID customerId) {
    if (customerId == null) {
      throw BusinessException.validation("customerId is required");
    }
    User customer =
        userRepository
            .findById(customerId)
            .filter(u -> u.getRole() == Role.CUSTOMER)
            .orElseThrow(() -> BusinessException.notFound("Customer", customerId));
    return customer.getId();
  }
}
