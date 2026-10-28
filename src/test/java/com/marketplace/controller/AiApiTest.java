package com.marketplace.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.marketplace.ai.ProviderCooldown;
import com.marketplace.ai.rag.PolicyKnowledge;
import com.marketplace.entity.User;
import com.marketplace.enums.Role;
import com.marketplace.enums.SellerStatus;
import com.marketplace.repository.AiChatSessionRepository;
import com.marketplace.support.TestSupport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * {@code /ai/*} theo features/ai/API_CONTRACT.md. Provider được giả bằng {@code ChatModel} của Mockito
 * (không gọi mạng); key giả để backend coi như đã cấu hình provider.
 */
@SpringBootTest(properties = "app.ai.api-key=test-key")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiApiTest {

  private static final String ASK = "/api/v1/ai/ask";

  @Autowired private MockMvc mvc;
  @Autowired private TestSupport support;
  @Autowired private AiChatSessionRepository sessions;
  @MockitoBean private ChatModel chatModel;
  @MockitoBean private PolicyKnowledge policyKnowledge; // mặc định trả rỗng: như khi RAG tắt
  @Autowired private ProviderCooldown cooldown;

  private User customer;
  private String guestIp; // mỗi test một IP, để bộ đếm hạn mức của guest không dồn giữa các test

  @BeforeEach
  void setUp() {
    customer = support.user(Role.CUSTOMER, null);
    guestIp = "10.0." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
    when(chatModel.call(any(Prompt.class))).thenAnswer(call -> reply("AI answer"));
    // Bean dùng chung cả context: xoá thời gian nghỉ do test 429 để lại.
    ReflectionTestUtils.setField(cooldown, "until", Instant.EPOCH);
  }

  @Test
  void afterAProvider429TheBackendSkipsTheProviderForTheWaitItAskedFor() throws Exception {
    when(chatModel.call(any(Prompt.class)))
        .thenThrow(new RuntimeException("HTTP 429 - Rate limit reached. Please try again in 20s."));
    askAsGuest("{\"message\":\"xin chào\"}").andExpect(jsonPath("$.data.answeredBy").value("RULES"));
    verify(chatModel, times(1)).call(any(Prompt.class));

    // Đang nghỉ: không gọi provider nữa, trả lời bằng luật ngay.
    askAs(customer, "{\"message\":\"xin chào\"}").andExpect(jsonPath("$.data.answeredBy").value("RULES"));
    verify(chatModel, times(1)).call(any(Prompt.class));
  }

  // ---------- trả lời bằng LLM ----------

  @Test
  void aGuestGetsAnAiAnswerAndNothingIsStored() throws Exception {
    long before = sessions.count();
    askAsGuest("{\"message\":\"tìm tai nghe\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.answer").value("AI answer"))
        .andExpect(jsonPath("$.data.answeredBy").value("AI"))
        .andExpect(jsonPath("$.data.sessionId").doesNotExist())
        .andExpect(jsonPath("$.data.products").isArray());
    assertThat(sessions.count()).isEqualTo(before);
  }

  @Test
  void aCustomerGetsASessionAndTheModelRemembersEarlierTurns() throws Exception {
    // Câu đầu: chưa có session, backend tạo mới.
    String sessionId =
        JsonPath.read(
            askAs(customer, "{\"message\":\"tìm tai nghe\"}")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.data.sessionId");

    // Câu hai cùng session: prompt gửi model phải có lượt hỏi đáp trước.
    ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
    askAs(customer, "{\"message\":\"cái rẻ nhất\",\"sessionId\":\"" + sessionId + "\"}")
        .andExpect(jsonPath("$.data.sessionId").value(sessionId));
    verify(chatModel, times(2)).call(prompt.capture());
    List<String> texts = prompt.getValue().getInstructions().stream().map(Message::getText).toList();
    assertThat(texts).contains("tìm tai nghe", "AI answer", "cái rẻ nhất");

    // Lịch sử: 4 tin, cũ trước.
    mvc.perform(get("/api/v1/ai/sessions/" + sessionId + "/messages").header(HttpHeaders.AUTHORIZATION, support.bearer(customer)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(4))
        .andExpect(jsonPath("$.data[0].role").value("USER"))
        .andExpect(jsonPath("$.data[0].content").value("tìm tai nghe"))
        .andExpect(jsonPath("$.data[3].role").value("ASSISTANT"));
    mvc.perform(get("/api/v1/ai/sessions").header(HttpHeaders.AUTHORIZATION, support.bearer(customer)))
        .andExpect(jsonPath("$.data[0].id").value(sessionId));
  }

  @Test
  void policyExcerptsGoIntoTheSystemPromptButNotIntoTheStoredQuestion() throws Exception {
    when(policyKnowledge.findRelevant("hoàn tiền thế nào"))
        .thenReturn(List.of("## Hoàn tiền\nHiện sàn chưa có quy trình hoàn tiền tự động cho khách."));

    String sessionId =
        JsonPath.read(
            askAs(customer, "{\"message\":\"hoàn tiền thế nào\"}")
                .andExpect(jsonPath("$.data.answeredBy").value("AI"))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.data.sessionId");

    ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
    verify(chatModel).call(prompt.capture());
    assertThat(prompt.getValue().getSystemMessage().getText())
        .contains("Policy excerpts:")
        .contains("chưa có quy trình hoàn tiền tự động");

    // Lịch sử chỉ có câu người dùng gõ, không lẫn trích đoạn.
    mvc.perform(get("/api/v1/ai/sessions/" + sessionId + "/messages").header(HttpHeaders.AUTHORIZATION, support.bearer(customer)))
        .andExpect(jsonPath("$.data[0].content").value("hoàn tiền thế nào"));
  }

  // ---------- trả lời bằng luật ----------

  @Test
  void aProviderErrorFallsBackToRulesAndTheTurnIsStillStored() throws Exception {
    when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("provider down"));
    String sessionId =
        JsonPath.read(
            askAs(customer, "{\"message\":\"thanh toán thế nào\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answeredBy").value("RULES"))
                .andExpect(jsonPath("$.data.answer").value(org.hamcrest.Matchers.containsString("COD")))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.data.sessionId");
    // Câu hỏi được advisor lưu trước khi gọi model; câu trả lời bằng luật được lưu sau, không bị lặp.
    mvc.perform(get("/api/v1/ai/sessions/" + sessionId + "/messages").header(HttpHeaders.AUTHORIZATION, support.bearer(customer)))
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].role").value("USER"))
        .andExpect(jsonPath("$.data[1].role").value("ASSISTANT"));
  }

  @Test
  void afterTwentyQuestionsRulesAnswerWithoutTheModelAndAfterSixtyItIs429() throws Exception {
    for (int i = 1; i <= 20; i++) {
      askAsGuest("{\"message\":\"xin chào\"}").andExpect(jsonPath("$.data.answeredBy").value("AI"));
    }
    clearInvocations(chatModel);
    for (int i = 21; i <= 60; i++) {
      askAsGuest("{\"message\":\"xin chào\"}").andExpect(jsonPath("$.data.answeredBy").value("RULES"));
    }
    verify(chatModel, never()).call(any(Prompt.class));
    askAsGuest("{\"message\":\"xin chào\"}")
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
  }

  // ---------- quyền và dữ liệu đầu vào ----------

  @Test
  void rejectsBadCallersSessionsAndMessages() throws Exception {
    User seller = support.user(Role.SELLER, SellerStatus.APPROVED);
    askAs(seller, "{\"message\":\"hi\"}").andExpect(status().isForbidden());
    askAsGuest("{\"message\":\"hi\",\"sessionId\":\"" + UUID.randomUUID() + "\"}")
        .andExpect(status().isUnauthorized());
    askAs(customer, "{\"message\":\"hi\",\"sessionId\":\"" + UUID.randomUUID() + "\"}")
        .andExpect(status().isNotFound());
    askAsGuest("{\"message\":\"  \"}").andExpect(status().isBadRequest());
    askAsGuest("{\"message\":\"" + "a".repeat(1001) + "\"}").andExpect(status().isBadRequest());

    // Session của customer khác: 404 như không tồn tại, cả khi hỏi lẫn khi xem lịch sử.
    String theirs =
        JsonPath.read(
            askAs(support.user(Role.CUSTOMER, null), "{\"message\":\"hi\"}").andReturn().getResponse().getContentAsString(),
            "$.data.sessionId");
    askAs(customer, "{\"message\":\"hi\",\"sessionId\":\"" + theirs + "\"}").andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/ai/sessions/" + theirs + "/messages").header(HttpHeaders.AUTHORIZATION, support.bearer(customer)))
        .andExpect(status().isNotFound());
    // Lịch sử chỉ dành cho customer.
    mvc.perform(get("/api/v1/ai/sessions")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/ai/sessions").header(HttpHeaders.AUTHORIZATION, support.bearer(seller)))
        .andExpect(status().isForbidden());
  }

  private ResultActions askAs(User who, String json) throws Exception {
    return support.send(mvc, post(ASK), who, json);
  }

  private ResultActions askAsGuest(String json) throws Exception {
    return mvc.perform(
        post(ASK).header("X-Forwarded-For", guestIp).contentType(MediaType.APPLICATION_JSON).content(json));
  }

  private static ChatResponse reply(String text) {
    return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
  }
}
