package com.marketplace.config;

import com.marketplace.service.impl.AiServiceImpl;
import java.time.Duration;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

/**
 * Provider dự phòng của trợ lý AI, chỉ dựng khi có {@code AI_FALLBACK_API_KEY}. Bean là {@link ChatClient}
 * chứ không phải ChatModel: có hai ChatModel thì Spring không còn dựng được {@code ChatClient.Builder} của
 * provider chính.
 */
@Configuration
@ConditionalOnExpression("!'${app.ai.fallback.api-key:}'.isBlank()")
public class AiFallbackConfig {

  /** Thấp để model bám kết quả tool, giống provider chính. */
  private static final double TEMPERATURE = 0.3;

  private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

  @Bean
  public ChatClient fallbackChatClient(
      @Value("${app.ai.fallback.api-key}") String apiKey,
      @Value("${app.ai.fallback.base-url}") String baseUrl,
      @Value("${app.ai.fallback.completions-path}") String completionsPath,
      @Value("${app.ai.fallback.model}") String model,
      ToolCallingManager toolCallingManager) {
    // Provider quá tải có thể không trả lời mà cũng không báo lỗi: đặt giới hạn để request của khách không treo.
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
    requestFactory.setReadTimeout(READ_TIMEOUT);
    OpenAiApi api =
        OpenAiApi.builder()
            .baseUrl(baseUrl)
            .apiKey(apiKey)
            .completionsPath(completionsPath)
            .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
            .build();
    OpenAiChatModel chatModel =
        OpenAiChatModel.builder()
            .openAiApi(api)
            .defaultOptions(OpenAiChatOptions.builder().model(model).temperature(TEMPERATURE).build())
            .toolCallingManager(toolCallingManager)
            // Đã là phương án cuối: lỗi thì báo ngay, không giữ request của khách để thử lại.
            .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
            .build();
    return ChatClient.builder(chatModel).defaultSystem(AiServiceImpl.SYSTEM_PROMPT).build();
  }
}
