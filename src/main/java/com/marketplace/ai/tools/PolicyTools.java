package com.marketplace.ai.tools;

import com.marketplace.ai.rag.PolicyKnowledge;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Tool chỉ đọc cho RAG điều khoản: LLM tự gọi khi người dùng hỏi về quy định của sàn. Không giữ trạng thái
 * nên dùng chung một bean cho mọi request (khác {@link ProductSearchTools} phải nhớ lần tìm cuối).
 */
@Component
@RequiredArgsConstructor
public class PolicyTools {

  private final PolicyKnowledge policyKnowledge;

  @Tool(
      description =
          "Search the marketplace terms and policies for customers: payment (COD, VNPAY), delivery, order "
              + "cancellation, refund, voucher, review and account rules. Use it for every question about how "
              + "the marketplace works. Returns up to 3 policy excerpts in Vietnamese; an empty list means no "
              + "policy covers the question.")
  public List<String> searchPolicies(
      @ToolParam(description = "The user's question about a marketplace rule, e.g. 'đơn VNPAY có hủy được không'")
          String question) {
    return policyKnowledge.findRelevant(question);
  }
}
