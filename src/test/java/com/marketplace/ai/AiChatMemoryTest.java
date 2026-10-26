package com.marketplace.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.ai.memory.AiChatMemory;
import com.marketplace.entity.AiChatSession;
import com.marketplace.enums.Role;
import com.marketplace.repository.AiChatSessionRepository;
import com.marketplace.repository.AiMessageRepository;
import com.marketplace.support.TestSupport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Chat memory: chỉ thêm, không xoá lịch sử; model chỉ nhận 20 tin gần nhất theo đúng thứ tự. */
@SpringBootTest
@ActiveProfiles("test")
class AiChatMemoryTest {

  @Autowired private AiChatMemory memory;
  @Autowired private AiChatSessionRepository sessions;
  @Autowired private AiMessageRepository messages;
  @Autowired private TestSupport support;

  @Test
  void keepsTheWholeHistoryButGivesTheModelOnlyTheLast20InOrder() {
    AiChatSession session =
        sessions.save(AiChatSession.builder().user(support.user(Role.CUSTOMER, null)).createdAt(Instant.now()).build());
    String id = session.getId().toString();

    // 13 lượt hỏi đáp = 26 tin, thêm từng cặp như advisor làm; tin SYSTEM và tin rỗng bị bỏ qua.
    for (int i = 1; i <= 13; i++) {
      memory.add(id, List.of(new UserMessage("q" + i), new AssistantMessage("a" + i)));
    }
    memory.add(id, List.of(new SystemMessage("system prompt"), new AssistantMessage(" ")));

    assertThat(messages.findBySessionIdOrderByCreatedAtAsc(session.getId())).hasSize(26);
    List<String> window = new ArrayList<>(memory.get(id).stream().map(Message::getText).toList());
    assertThat(window).hasSize(AiChatMemory.WINDOW);
    assertThat(window.get(0)).isEqualTo("q4"); // 6 tin đầu (q1..a3) nằm ngoài cửa sổ
    assertThat(window.get(19)).isEqualTo("a13");
    assertThat(memory.get(id).get(0)).isInstanceOf(UserMessage.class);
  }
}
