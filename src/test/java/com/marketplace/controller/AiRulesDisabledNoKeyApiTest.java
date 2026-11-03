package com.marketplace.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Không có AI_API_KEY và đã tắt trả lời bằng luật: báo 503 thay vì trả lời bằng luật. */
@SpringBootTest(properties = {"app.ai.api-key=", "app.ai.rules-enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiRulesDisabledNoKeyApiTest {

  @Autowired private MockMvc mvc;

  @Test
  void withoutAKeyItIs503() throws Exception {
    mvc.perform(
            post("/api/v1/ai/ask")
                .header("X-Forwarded-For", "10.2.0.1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"xin chào\"}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error.code").value("AI_UNAVAILABLE"));
  }
}
