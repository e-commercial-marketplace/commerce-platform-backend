package com.marketplace.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
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
import com.marketplace.service.impl.AiServiceImpl;
import com.marketplace.support.TestSupport;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Provider chính lỗi thì provider dự phòng trả lời; cả hai đều lỗi mới trả lời bằng luật. Cả hai đều là model giả. */
@SpringBootTest(properties = {"app.ai.api-key=primary-test-key"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AiFallbackApiTest.FallbackConfig.class)
class AiFallbackApiTest {

  private static final ChatModel FALLBACK_MODEL = mock(ChatModel.class);

  @TestConfiguration
  static class FallbackConfig {
    @Bean
    ChatClient fallbackChatClient() {
      return ChatClient.builder(FALLBACK_MODEL).defaultSystem(AiServiceImpl.SYSTEM_PROMPT).build();
    }
  }

  @Autowired private MockMvc mvc;
  @Autowired private TestSupport support;
  @Autowired private ProviderCooldown cooldown;
  @Autowired private AiServiceImpl aiService;
  @MockitoBean private ChatModel chatModel;
  @MockitoBean private PolicyKnowledge policyKnowledge;

  private String guestIp;

  @BeforeEach
  void setUp() {
    guestIp = "10.1." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
    reset(FALLBACK_MODEL);
    when(chatModel.call(any(Prompt.class))).thenAnswer(call -> reply("primary answer"));
    when(FALLBACK_MODEL.call(any(Prompt.class))).thenAnswer(call -> reply("fallback answer"));
    ReflectionTestUtils.setField(cooldown, "until", Instant.EPOCH);
    ProviderCooldown fallbackCooldown = (ProviderCooldown) ReflectionTestUtils.getField(aiService, "fallbackCooldown");
    ReflectionTestUtils.setField(fallbackCooldown, "until", Instant.EPOCH);
  }

  @Test
  void aHealthyPrimaryNeverCallsTheFallback() throws Exception {
    askAsGuest("xin chào").andExpect(jsonPath("$.data.answer").value("primary answer"));
    verify(FALLBACK_MODEL, never()).call(any(Prompt.class));
  }

  @Test
  void aPrimaryFailureIsAnsweredByTheFallback() throws Exception {
    when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("provider down"));
    askAsGuest("xin chào")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.answer").value("fallback answer"))
        .andExpect(jsonPath("$.data.answeredBy").value("AI"));
  }

  @Test
  void whileThePrimaryCoolsDownOnlyTheFallbackIsCalled() throws Exception {
    when(chatModel.call(any(Prompt.class)))
        .thenThrow(new RuntimeException("HTTP 429 - Rate limit reached. Please try again in 20s."));
    askAsGuest("câu một").andExpect(jsonPath("$.data.answer").value("fallback answer"));
    askAsGuest("câu hai").andExpect(jsonPath("$.data.answer").value("fallback answer"));

    verify(chatModel, times(1)).call(any(Prompt.class));
    verify(FALLBACK_MODEL, times(2)).call(any(Prompt.class));
  }

  @Test
  void whenBothProvidersFailTheRulesAnswer() throws Exception {
    when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("provider down"));
    when(FALLBACK_MODEL.call(any(Prompt.class))).thenThrow(new RuntimeException("provider down"));
    askAsGuest("xin chào").andExpect(jsonPath("$.data.answeredBy").value("RULES"));
  }

  @Test
  void theFallbackSeesEarlierTurnsAndTheQuestionIsStoredOnce() throws Exception {
    User customer = support.user(Role.CUSTOMER, null);
    String sessionId =
        JsonPath.read(
            ask(customer, "{\"message\":\"câu một\"}").andReturn().getResponse().getContentAsString(),
            "$.data.sessionId");

    when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("provider down"));
    ask(customer, "{\"message\":\"câu hai\",\"sessionId\":\"" + sessionId + "\"}")
        .andExpect(jsonPath("$.data.answer").value("fallback answer"));

    ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
    verify(FALLBACK_MODEL).call(prompt.capture());
    List<String> sent =
        prompt.getValue().getInstructions().stream()
            .filter(message -> !(message instanceof org.springframework.ai.chat.messages.SystemMessage))
            .map(Message::getText)
            .toList();
    assertThat(sent).containsExactly("câu một", "primary answer", "câu hai");

    mvc.perform(
            get("/api/v1/ai/sessions/" + sessionId + "/messages")
                .header(HttpHeaders.AUTHORIZATION, support.bearer(customer)))
        .andExpect(jsonPath("$.data.length()").value(4))
        .andExpect(jsonPath("$.data[2].content").value("câu hai"))
        .andExpect(jsonPath("$.data[3].content").value("fallback answer"));
  }

  private ResultActions askAsGuest(String message) throws Exception {
    return mvc.perform(
        post("/api/v1/ai/ask")
            .with(request -> {
              request.setRemoteAddr(guestIp);
              return request;
            })
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"message\":\"" + message + "\"}"));
  }

  private ResultActions ask(User user, String body) throws Exception {
    return mvc.perform(
        post("/api/v1/ai/ask")
            .header(HttpHeaders.AUTHORIZATION, support.bearer(user))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private static ChatResponse reply(String text) {
    return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
  }
}
