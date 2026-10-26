package com.marketplace.ai;

import com.marketplace.common.util.TimeProvider;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Đếm số câu hỏi AI của mỗi người trong từng phút (cửa sổ cố định theo phút đồng hồ). Key là
 * {@code user:<id>} cho customer hoặc {@code ip:<ip>} cho guest.
 *
 * <p>shortcut: bộ đếm nằm trong RAM của một instance, khởi động lại là về 0. Đủ khi Render chạy một
 * instance; chạy nhiều instance thì chuyển bộ đếm sang Redis (INCR + EXPIRE).
 */
@Component
@RequiredArgsConstructor
public class AiRateLimiter {

  /** Câu 1 tới {@value} trong một phút được dùng LLM. */
  public static final int AI_LIMIT = 20;

  /** Câu {@code AI_LIMIT + 1} tới {@value} trả lời bằng luật; quá số này thì chặn (429). */
  public static final int HARD_LIMIT = 60;

  /** Mức phục vụ cho câu hỏi hiện tại. */
  public enum Level {
    AI,
    RULES,
    BLOCKED
  }

  private record Window(long minute, AtomicInteger count) {}

  private final TimeProvider timeProvider;
  private final Map<String, Window> windows = new ConcurrentHashMap<>();

  /** Tính thêm một câu cho {@code key} và trả mức phục vụ của câu đó. */
  public Level acquire(String key) {
    long minute = timeProvider.now().getEpochSecond() / 60;
    // compute chạy nguyên tử cho từng key: hai request cùng lúc không làm đếm sai.
    Window window =
        windows.compute(
            key, (k, old) -> old == null || old.minute() != minute ? new Window(minute, new AtomicInteger()) : old);
    int count = window.count().incrementAndGet();
    // Dọn các key của phút cũ để Map không lớn mãi (chỉ chạy khi Map đã khá nhiều key).
    if (windows.size() > 10_000) {
      windows.values().removeIf(w -> w.minute() != minute);
    }
    if (count <= AI_LIMIT) {
      return Level.AI;
    }
    return count <= HARD_LIMIT ? Level.RULES : Level.BLOCKED;
  }
}
