package com.marketplace.service.impl;

import com.marketplace.config.AppMailProperties;
import com.marketplace.service.EmailService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Gửi email qua API HTTPS của Brevo, vì Render gói miễn phí chặn các cổng SMTP. Khi
 * {@code app.mail.enabled=false} thì không gửi mà chỉ ghi mã ra log, để chạy local không cần tài
 * khoản email. Production phải bật email, vì log không được chứa mã thật.
 */
@Slf4j // Lombok tạo biến "log" để ghi log
@Service
public class EmailServiceImpl implements EmailService {

  private static final String BREVO_SEND_URL = "https://api.brevo.com/v3/smtp/email";
  // Lệnh gửi nằm trong transaction của sendResetCode nên không được chờ lâu.
  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  private final RestClient restClient;
  private final AppMailProperties mailProperties; // bật/tắt gửi thật, người gửi và API key

  @Autowired
  public EmailServiceImpl(RestClient.Builder builder, AppMailProperties mailProperties) {
    this(builder.requestFactory(timeoutFactory()).build(), mailProperties);
  }

  /** Test truyền RestClient giả vào đây để không gọi Brevo thật. */
  EmailServiceImpl(RestClient restClient, AppMailProperties mailProperties) {
    this.restClient = restClient;
    this.mailProperties = mailProperties;
  }

  private static SimpleClientHttpRequestFactory timeoutFactory() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(TIMEOUT);
    factory.setReadTimeout(TIMEOUT);
    return factory;
  }

  @Override
  public void sendPasswordResetCode(String to, String code, long validMinutes) {
    // Bước 1: email đang tắt (phát triển local) thì chỉ ghi ra log rồi dừng.
    if (!mailProperties.enabled()) {
      log.warn("Email is disabled (app.mail.enabled=false). Password reset code for {}: {}", to, code);
      return;
    }
    // Bước 2: soạn email dạng chữ thường (không HTML), đủ cho một mã số.
    String text =
        "Mã đặt lại mật khẩu của bạn là: "
            + code
            + "\n\nMã có hiệu lực trong "
            + validMinutes
            + " phút và chỉ dùng được một lần."
            + "\nNếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này.";
    Map<String, Object> body =
        Map.of(
            "sender", Map.of("email", mailProperties.from()),
            "to", List.of(Map.of("email", to)),
            "subject", "Mã đặt lại mật khẩu",
            "textContent", text);
    // Bước 3: gửi. Lỗi (sai API key, mất mạng, Brevo từ chối) ném RestClientException cho nơi gọi.
    restClient
        .post()
        .uri(BREVO_SEND_URL)
        .header("api-key", mailProperties.brevoApiKey())
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .toBodilessEntity();
  }
}
