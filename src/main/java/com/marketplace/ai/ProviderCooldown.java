package com.marketplace.ai;

import com.marketplace.common.util.TimeProvider;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Tạm nghỉ gọi provider sau khi nó trả 429 (hết hạn mức token/request). Trong lúc nghỉ, mọi câu hỏi trả
 * lời bằng luật ngay, thay vì lần nào cũng gọi provider rồi mới bị từ chối. Hạn mức là của cả tài khoản
 * provider, nên thời gian nghỉ dùng chung cho mọi người dùng.
 *
 * <p>shortcut: trạng thái nằm trong RAM của một instance, đủ khi Render chạy một instance.
 */
@Component
@RequiredArgsConstructor
public class ProviderCooldown {

  /** Nghỉ bao lâu khi provider không nói phải chờ bao nhiêu. */
  static final Duration DEFAULT_WAIT = Duration.ofSeconds(30);

  /** Không nghỉ quá lâu dù provider bảo chờ lâu hơn (thử lại sẽ biết). */
  static final Duration MAX_WAIT = Duration.ofSeconds(60);

  // Thông báo kiểu OpenAI/Groq: "Please try again in 10.90s".
  private static final Pattern RETRY_IN =
      Pattern.compile("try again in (\\d+(?:\\.\\d+)?)\\s*s", Pattern.CASE_INSENSITIVE);

  private final TimeProvider timeProvider;
  private volatile Instant until = Instant.EPOCH;

  /** Đang trong thời gian nghỉ thì không gọi provider. */
  public boolean active() {
    return timeProvider.now().isBefore(until);
  }

  /** Gọi khi provider lỗi: chỉ lỗi hết hạn mức (429) mới bắt đầu nghỉ; lỗi khác thì lần sau thử lại ngay. */
  public void onFailure(RuntimeException error) {
    String message = String.valueOf(error.getMessage());
    String lower = message.toLowerCase(Locale.ROOT);
    if (!lower.contains("429") && !lower.contains("rate limit")) {
      return;
    }
    Duration wait = DEFAULT_WAIT;
    Matcher m = RETRY_IN.matcher(message);
    if (m.find()) {
      // Làm tròn lên một giây cho chắc, và không quá MAX_WAIT.
      long seconds = (long) Math.ceil(Double.parseDouble(m.group(1))) + 1;
      wait = Duration.ofSeconds(Math.min(seconds, MAX_WAIT.getSeconds()));
    }
    until = timeProvider.now().plus(wait);
  }
}
