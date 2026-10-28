package com.marketplace.ai.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.marketplace.ai.rag.PolicyKnowledge;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tool searchPolicies chỉ chuyển câu hỏi model gửi sang tìm điều khoản và trả lại các đoạn tìm được. */
class PolicyToolsTest {

  private final PolicyKnowledge policyKnowledge = mock(PolicyKnowledge.class);
  private final PolicyTools tools = new PolicyTools(policyKnowledge);

  @Test
  void returnsTheExcerptsFoundForTheModelsQuestion() {
    when(policyKnowledge.findRelevant("đơn VNPAY có hủy được không"))
        .thenReturn(List.of("## Hủy đơn\nĐơn VNPAY đã thanh toán thì chưa hủy được."));

    assertThat(tools.searchPolicies("đơn VNPAY có hủy được không"))
        .containsExactly("## Hủy đơn\nĐơn VNPAY đã thanh toán thì chưa hủy được.");
  }

  @Test
  void anEmptyResultMeansNoPolicyCoversTheQuestion() {
    assertThat(tools.searchPolicies("thời tiết hôm nay")).isEmpty();
  }
}
