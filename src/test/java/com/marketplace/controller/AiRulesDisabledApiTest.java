package com.marketplace.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketplace.ai.ProviderCooldown;
import com.marketplace.ai.rag.PolicyKnowledge;
import com.marketplace.entity.User;
import com.marketplace.enums.Role;
import com.marketplace.repository.AiChatSessionRepository;
import com.marketplace.support.TestSupport;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** APP_AI_RULES_ENABLED=false: API không bao giờ trả lời bằng luật. Provider được giả bằng ChatModel. */
@SpringBootTest(properties = {"app.ai.api-key=test-key", "app.ai.rules-enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiRulesDisabledApiTest {

  private static final String ASK = "/api/v1/ai/ask";
  private static final String QUESTION = "{\"message\":\"xin chào\"}";

  @Autowired private MockMvc mvc;
  @Autowired private TestSupport support;
  @Autowired private AiChatSessionRepository sessions;
  @Autowired private ProviderCooldown cooldown;
  @MockitoBean private ChatModel chatModel;
  @MockitoBean private PolicyKnowledge policyKnowledge;

  private String guestIp; // mỗi test một IP, để bộ đếm hạn mức của guest không dồn lại

  @BeforeEach
  void setUp() {
    guestIp = "10.1." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
    when(chatModel.call(any(Prompt.class))).thenAnswer(call -> reply("AI answer"));
    ReflectionTestUtils.setField(cooldown, "until", Instant.EPOCH);
  }

  @Test
  void theLlmStillAnswers() throws Exception {
    askAsGuest().andExpect(status().isOk()).andExpect(jsonPath("$.data.answeredBy").value("AI"));
  }

  @Test
  void aProviderFailureIs503() throws Exception {
    when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("provider down"));

    askAsGuest()
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error.code").value("AI_UNAVAILABLE"))
        .andExpect(jsonPath("$.error.message").value("Vui lòng thử lại sau"));
  }

  @Test
  void anEmptyAnswerIs503() throws Exception {
    when(chatModel.call(any(Prompt.class))).thenAnswer(call -> reply(" "));

    askAsGuest().andExpect(status().isServiceUnavailable());
  }

  @Test
  void whileCoolingDownItIs503AndNoSessionIsCreated() throws Exception {
    ReflectionTestUtils.setField(cooldown, "until", Instant.now().plusSeconds(60));
    User customer = support.user(Role.CUSTOMER, null);
    long before = sessions.count();

    support
        .send(mvc, post(ASK), customer, QUESTION)
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error.code").value("AI_UNAVAILABLE"));

    assertThat(sessions.count()).isEqualTo(before);
  }

  @Test
  void theTwentyFirstQuestionIs429() throws Exception {
    for (int i = 1; i <= 20; i++) {
      askAsGuest().andExpect(jsonPath("$.data.answeredBy").value("AI"));
    }

    askAsGuest()
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"))
        .andExpect(jsonPath("$.error.message").value("Vui lòng thử lại sau"));
  }

  private ResultActions askAsGuest() throws Exception {
    return mvc.perform(
        post(ASK).header("X-Forwarded-For", guestIp).contentType(MediaType.APPLICATION_JSON).content(QUESTION));
  }

  private static ChatResponse reply(String text) {
    return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
  }
}
