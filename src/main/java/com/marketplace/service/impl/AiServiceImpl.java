package com.marketplace.service.impl;

import com.marketplace.ai.AiRateLimiter;
import com.marketplace.ai.memory.AiChatMemory;
import com.marketplace.ai.rules.RuleBasedAnswerer;
import com.marketplace.ai.rules.RuleBasedAnswerer.RuleAnswer;
import com.marketplace.ai.tools.AiProductSearch;
import com.marketplace.ai.tools.ProductSearchTools;
import com.marketplace.common.exception.BusinessException;
import com.marketplace.common.util.TimeProvider;
import com.marketplace.dto.request.AiAskRequest;
import com.marketplace.dto.response.AiChatSessionResponse;
import com.marketplace.dto.response.AiMessageResponse;
import com.marketplace.dto.response.AiProductSearchResponse;
import com.marketplace.entity.AiChatSession;
import com.marketplace.enums.AiAnswerSource;
import com.marketplace.enums.Role;
import com.marketplace.mapper.AiMapper;
import com.marketplace.repository.AiChatSessionRepository;
import com.marketplace.repository.AiMessageRepository;
import com.marketplace.repository.UserRepository;
import com.marketplace.security.AuthUser;
import com.marketplace.service.AiService;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trợ lý AI: LLM (qua Spring AI) cùng tool tìm product và chat memory, có bộ trả lời bằng luật thay thế.
 *
 * <p>{@link #ask} cố ý KHÔNG nằm trong một transaction: một lần gọi LLM có thể mất vài giây, không nên giữ
 * kết nối DB suốt lúc đó. Mỗi lần đọc/ghi (session, memory, tìm product) tự có transaction riêng.
 */
@Slf4j
@Service
public class AiServiceImpl implements AiService {

  /** Luật cho model: chỉ nói dựa trên kết quả tool, không bịa, tiền là VND. */
  static final String SYSTEM_PROMPT =
      """
      You are the shopping assistant of a Vietnamese online marketplace. Answer in the user's language.
      - For any question about products, prices or stock, call the searchProducts tool and answer only
        from its results. Never invent products, prices, stock or ratings.
      - Prices are in VND. If the user gives another currency, ask them to use VND; do not convert.
      - If the tool returns nothing, say so and suggest other keywords or a wider price range.
      - You cannot place, change or cancel orders. Keep answers short.
      """;

  private final ChatClient chatClient;
  private final AiChatMemory chatMemory;
  private final AiProductSearch productSearch;
  private final RuleBasedAnswerer ruleBasedAnswerer;
  private final AiRateLimiter rateLimiter;
  private final AiChatSessionRepository sessionRepository;
  private final AiMessageRepository messageRepository;
  private final UserRepository userRepository;
  private final AiMapper aiMapper;
  private final TimeProvider timeProvider;
  private final boolean providerConfigured;

  public AiServiceImpl(
      ChatClient.Builder chatClientBuilder,
      AiChatMemory chatMemory,
      AiProductSearch productSearch,
      RuleBasedAnswerer ruleBasedAnswerer,
      AiRateLimiter rateLimiter,
      AiChatSessionRepository sessionRepository,
      AiMessageRepository messageRepository,
      UserRepository userRepository,
      AiMapper aiMapper,
      TimeProvider timeProvider,
      @Value("${app.ai.api-key:}") String apiKey) {
    this.chatClient = chatClientBuilder.defaultSystem(SYSTEM_PROMPT).build();
    this.chatMemory = chatMemory;
    this.productSearch = productSearch;
    this.ruleBasedAnswerer = ruleBasedAnswerer;
    this.rateLimiter = rateLimiter;
    this.sessionRepository = sessionRepository;
    this.messageRepository = messageRepository;
    this.userRepository = userRepository;
    this.aiMapper = aiMapper;
    this.timeProvider = timeProvider;
    this.providerConfigured = apiKey != null && !apiKey.isBlank();
  }

  @Override
  public AiProductSearchResponse ask(AuthUser caller, AiAskRequest request, String clientIp) {
    // Bước 1: ai được hỏi. Seller/admin không có quyền dùng AI; guest không được có session.
    if (caller != null && caller.role() != Role.CUSTOMER) {
      throw BusinessException.forbidden("Only customers and guests can use the AI assistant");
    }
    if (caller == null && request.sessionId() != null) {
      throw BusinessException.unauthorized("Log in to use a saved AI session");
    }
    // Bước 2: hạn mức theo phút: 1-20 dùng LLM, 21-60 trả lời bằng luật, quá 60 thì 429.
    AiRateLimiter.Level level = rateLimiter.acquire(caller != null ? "user:" + caller.id() : "ip:" + clientIp);
    if (level == AiRateLimiter.Level.BLOCKED) {
      throw BusinessException.rateLimited("Too many AI questions, try again in a minute");
    }
    // Bước 3: session của customer (mới hoặc của chính mình); guest không có.
    UUID sessionId = caller == null ? null : resolveSession(caller.id(), request.sessionId());
    String message = request.message().trim();

    // Bước 4: thử LLM khi được phép.
    boolean userMessageSaved = false;
    if (level == AiRateLimiter.Level.AI && providerConfigured) {
      ProductSearchTools tools = new ProductSearchTools(productSearch);
      try {
        ChatClient.ChatClientRequestSpec prompt = chatClient.prompt().user(message).tools(tools);
        if (sessionId != null) {
          // Advisor đọc 20 tin gần nhất đưa vào prompt, rồi lưu câu hỏi và câu trả lời vào ai_messages.
          prompt = prompt.advisors(MessageChatMemoryAdvisor.builder(chatMemory).conversationId(sessionId.toString()).build());
        }
        String answer = prompt.call().content();
        if (answer != null && !answer.isBlank()) {
          AiProductSearch.Result result = tools.lastResult();
          return new AiProductSearchResponse(
              answer,
              result == null ? null : result.query(),
              result == null ? List.of() : result.products(),
              sessionId,
              AiAnswerSource.AI);
        }
      } catch (BusinessException e) {
        throw e;
      } catch (RuntimeException e) {
        // Provider lỗi (sai key, hết hạn mức, mạng...): ghi log, không lộ chi tiết, chuyển sang luật.
        log.warn("AI provider call failed, answering with rules: {}", e.toString());
      }
      // Advisor lưu câu hỏi TRƯỚC khi gọi model, nên tới đây câu hỏi đã có trong session.
      userMessageSaved = sessionId != null;
    }

    // Bước 5: trả lời bằng luật; customer vẫn được lưu lượt hỏi đáp này vào session.
    RuleAnswer rules = ruleBasedAnswerer.answer(message);
    if (sessionId != null) {
      chatMemory.add(
          sessionId.toString(),
          userMessageSaved
              ? List.of(new AssistantMessage(rules.answer()))
              : List.of(new UserMessage(message), new AssistantMessage(rules.answer())));
    }
    return new AiProductSearchResponse(rules.answer(), rules.query(), rules.products(), sessionId, AiAnswerSource.RULES);
  }

  @Override
  @Transactional(readOnly = true)
  public List<AiChatSessionResponse> listSessions(UUID customerId) {
    return sessionRepository.findByUserIdOrderByCreatedAtDescIdAsc(customerId).stream()
        .map(aiMapper::toSession)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<AiMessageResponse> listMessages(UUID customerId, UUID sessionId) {
    findOwnedSession(customerId, sessionId);
    return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
        .map(aiMapper::toMessage)
        .toList();
  }

  /** Không gửi id thì mở session mới; có id thì phải là session của chính customer (không thì 404). */
  private UUID resolveSession(UUID customerId, UUID sessionId) {
    if (sessionId != null) {
      return findOwnedSession(customerId, sessionId).getId();
    }
    AiChatSession session =
        AiChatSession.builder()
            .user(userRepository.getReferenceById(customerId))
            .createdAt(timeProvider.now())
            .build();
    return sessionRepository.save(session).getId();
  }

  /** Session của người khác trả 404 giống như không tồn tại, để không lộ id có thật. */
  private AiChatSession findOwnedSession(UUID customerId, UUID sessionId) {
    return sessionRepository
        .findByIdAndUserId(sessionId, customerId)
        .orElseThrow(() -> BusinessException.notFound("AI session", sessionId));
  }
}
