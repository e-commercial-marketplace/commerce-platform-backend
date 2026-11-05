package com.marketplace.service.impl;

import com.marketplace.ai.AiRateLimiter;
import com.marketplace.ai.ProviderCooldown;
import com.marketplace.ai.memory.AiChatMemory;
import com.marketplace.ai.rules.RuleBasedAnswerer;
import com.marketplace.ai.rules.RuleBasedAnswerer.RuleAnswer;
import com.marketplace.ai.tools.AiProductSearch;
import com.marketplace.ai.tools.PolicyTools;
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
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
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

  /**
   * Luật cho model. Viết bằng tiếng Anh vì model làm theo hướng dẫn tiếng Anh ổn định hơn, nhưng dặn trả
   * lời bằng ngôn ngữ của người dùng. Có ví dụ mẫu để model chủ động dùng tool thay vì chỉ hỏi lại.
   */
  public static final String SYSTEM_PROMPT =
      """
      You are the friendly shopping assistant of a Vietnamese online marketplace.

      Language: reply in the user's language (Vietnamese when they write Vietnamese). Be warm and short.

      Categories on sale now: {categories}

      Tools (read-only):
      - listCategories: the category names the shop sells. Call it only when the user asks what the shop
        sells, never to find a category for a search.
      - searchProducts(text, category, minPrice, maxPrice, minRating, sort): at most 5 listed products.
      - searchPolicies(question): up to 3 excerpts of the marketplace terms for customers.

      Rules:
      1. Be proactive. Never answer only with a question when a tool can help.
         - "I want to buy something", "show me products": call searchProducts with no filters, then show a
           few categories from the list above and the products, and ask what they need.
         - A product type ("headphones", "laptop", "điện thoại"): call searchProducts with the matching
           category from the list above and/or text.
         - A price or rating ("dưới 2 triệu", "từ 4 sao"): put it in minPrice/maxPrice/minRating.
           "rẻ nhất" means sort=price_asc, "đắt nhất" sort=price_desc, "tốt nhất" sort=rating_desc.
         - Call searchProducts at most 2 times for one user message, then answer with what you have.
      2. Answer only from tool results. Never invent products, prices, stock, ratings or categories.
         If a search returns nothing, say so and offer a wider search (another category or price range).
      2b. Availability: every product has a stock number (units left). Use it to answer "còn hàng không":
          stock 0 means sold out; otherwise say it is available (mention the number when it is 5 or less).
          If the question is about an earlier product, call searchProducts again before answering.
      3. List products as "- Name: price ₫" with prices in VND written with dots, e.g. 2.990.000 ₫. Then
         suggest one useful next step (narrow by price, compare two items, another category).
      4. Prices are in VND. If the user uses another currency, ask them to give the amount in VND.
      5. You cannot place, change or cancel orders or payments; explain that the app does it.
      6. If the user is unhappy or the message is unclear ("?"), apologise in one sentence and give two
         example questions, e.g. "tìm tai nghe dưới 2 triệu" or "laptop rẻ nhất".
      7. Questions about marketplace rules (payment, delivery, cancellation, refund, voucher, review,
         account): call searchPolicies and answer only from its excerpts. Do not add anything the excerpts
         do not say (no support team, hotline, email or extra steps). If it returns nothing, say you are
         not sure.
      """;

  // Câu thông báo chung cho mọi trường hợp không trả lời được.
  private static final String AI_UNAVAILABLE_MESSAGE =
      "Vui lòng thử lại sau";

  private final ChatClient chatClient;
  private final ChatClient fallbackChatClient; // null khi chưa cấu hình provider dự phòng
  private final AiChatMemory chatMemory;
  private final PolicyTools policyTools;
  private final AiProductSearch productSearch;
  private final RuleBasedAnswerer ruleBasedAnswerer;
  private final AiRateLimiter rateLimiter;
  private final ProviderCooldown cooldown;
  private final ProviderCooldown fallbackCooldown;
  private final AiChatSessionRepository sessionRepository;
  private final AiMessageRepository messageRepository;
  private final UserRepository userRepository;
  private final AiMapper aiMapper;
  private final TimeProvider timeProvider;
  private final boolean providerConfigured;
  private final boolean rulesEnabled;

  public AiServiceImpl(
      ChatClient.Builder chatClientBuilder,
      AiChatMemory chatMemory,
      PolicyTools policyTools,
      AiProductSearch productSearch,
      RuleBasedAnswerer ruleBasedAnswerer,
      AiRateLimiter rateLimiter,
      ProviderCooldown cooldown,
      AiChatSessionRepository sessionRepository,
      AiMessageRepository messageRepository,
      UserRepository userRepository,
      AiMapper aiMapper,
      TimeProvider timeProvider,
      @Qualifier("fallbackChatClient") ObjectProvider<ChatClient> fallbackChatClient,
      @Value("${app.ai.api-key:}") String apiKey,
      @Value("${app.ai.rules-enabled:true}") boolean rulesEnabled) {
    this.chatClient = chatClientBuilder.defaultSystem(SYSTEM_PROMPT).build();
    this.chatMemory = chatMemory;
    this.policyTools = policyTools;
    this.productSearch = productSearch;
    this.ruleBasedAnswerer = ruleBasedAnswerer;
    this.rateLimiter = rateLimiter;
    this.cooldown = cooldown;
    this.fallbackChatClient = fallbackChatClient.getIfAvailable();
    // Hạn mức là của từng provider, nên provider dự phòng có thời gian nghỉ riêng.
    this.fallbackCooldown = new ProviderCooldown(timeProvider);
    this.sessionRepository = sessionRepository;
    this.messageRepository = messageRepository;
    this.userRepository = userRepository;
    this.aiMapper = aiMapper;
    this.timeProvider = timeProvider;
    this.providerConfigured = apiKey != null && !apiKey.isBlank();
    this.rulesEnabled = rulesEnabled;
  }

  @Override
  public AiProductSearchResponse answerQuestion(AuthUser caller, AiAskRequest request, String clientIp) {
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
      throw BusinessException.rateLimited(AI_UNAVAILABLE_MESSAGE);
    }
    // Đã tắt trả lời bằng luật mà biết trước LLM không dùng được: báo lỗi trước khi tạo session.
    if (!rulesEnabled) {
      if (level == AiRateLimiter.Level.RULES) {
        throw BusinessException.rateLimited(AI_UNAVAILABLE_MESSAGE);
      }
      if (!primaryAvailable() && !fallbackAvailable()) {
        throw BusinessException.aiUnavailable(AI_UNAVAILABLE_MESSAGE);
      }
    }
    // Bước 3: session của customer (mới hoặc của chính mình); guest không có.
    UUID sessionId = caller == null ? null : resolveSession(caller.id(), request.sessionId());
    String message = request.message().trim();

    // Bước 4: thử LLM khi được phép (trong hạn mức, có provider không đang bắt chờ sau 429): provider chính
    // trước, lỗi thì provider dự phòng.
    boolean userMessageSaved = false;
    if (level == AiRateLimiter.Level.AI && primaryAvailable()) {
      AiProductSearchResponse answer = askModel(chatClient, cooldown, message, sessionId, false);
      if (answer != null) {
        return answer;
      }
      // Advisor lưu câu hỏi TRƯỚC khi gọi model, nên tới đây câu hỏi đã có trong session.
      userMessageSaved = sessionId != null;
    }
    if (level == AiRateLimiter.Level.AI && fallbackAvailable()) {
      AiProductSearchResponse answer =
          askModel(fallbackChatClient, fallbackCooldown, message, sessionId, userMessageSaved);
      if (answer != null) {
        return answer;
      }
      userMessageSaved = sessionId != null;
    }

    // Provider vừa lỗi hoặc trả rỗng mà đã tắt trả lời bằng luật: không còn cách trả lời nào khác.
    if (!rulesEnabled) {
      throw BusinessException.aiUnavailable(AI_UNAVAILABLE_MESSAGE);
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

  private boolean primaryAvailable() {
    return providerConfigured && !cooldown.active();
  }

  private boolean fallbackAvailable() {
    return fallbackChatClient != null && !fallbackCooldown.active();
  }

  /**
   * Gọi một provider và trả response, hoặc {@code null} nếu provider lỗi hoặc trả câu rỗng.
   *
   * @param userMessageSaved provider trước đã lưu câu hỏi vào session: không dùng advisor nữa, vì advisor sẽ
   *     lưu câu hỏi thêm một lần
   */
  private AiProductSearchResponse askModel(
      ChatClient client, ProviderCooldown clientCooldown, String message, UUID sessionId, boolean userMessageSaved) {
    ProductSearchTools tools = new ProductSearchTools(productSearch);
    try {
      // Model tự chọn tool: searchProducts cho câu hỏi sản phẩm, searchPolicies cho câu hỏi về quy định.
      // Đưa sẵn tên danh mục vào prompt để model khỏi tốn một lượt gọi listCategories trước khi tìm.
      String categories = String.join(", ", productSearch.categoryNames());
      ChatClient.ChatClientRequestSpec prompt =
          client
              .prompt()
              .system(system -> system.param("categories", categories))
              .user(message)
              .tools(tools, policyTools);
      if (sessionId != null && userMessageSaved) {
        // Tin cuối trong memory chính là câu hỏi này, đã có ở .user(message) phía trên.
        List<Message> history = chatMemory.get(sessionId.toString());
        prompt = prompt.messages(history.subList(0, Math.max(0, history.size() - 1)));
      } else if (sessionId != null) {
        // Advisor đọc 10 tin gần nhất đưa vào prompt, rồi lưu câu hỏi và câu trả lời vào ai_messages.
        prompt = prompt.advisors(MessageChatMemoryAdvisor.builder(chatMemory).conversationId(sessionId.toString()).build());
      }
      String answer = prompt.call().content();
      if (answer == null || answer.isBlank()) {
        return null;
      }
      if (sessionId != null && userMessageSaved) {
        chatMemory.add(sessionId.toString(), List.of(new AssistantMessage(answer)));
      }
      AiProductSearch.Result result = tools.lastResult();
      return new AiProductSearchResponse(
          answer,
          result == null ? null : result.query(),
          result == null ? List.of() : result.products(),
          sessionId,
          AiAnswerSource.AI);
    } catch (BusinessException e) {
      throw e;
    } catch (RuntimeException e) {
      // Provider lỗi (sai key, hết hạn mức, mạng...): ghi log, không lộ chi tiết, chuyển sang bước sau.
      log.warn("AI provider call failed: {}", e.toString());
      clientCooldown.onFailure(e); // 429 thì tạm nghỉ gọi provider này một lúc
      return null;
    }
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
  public List<AiMessageResponse> listSessionMessages(UUID customerId, UUID sessionId) {
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
