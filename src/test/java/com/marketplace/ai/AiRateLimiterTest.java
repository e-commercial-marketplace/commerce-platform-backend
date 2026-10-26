package com.marketplace.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.marketplace.ai.AiRateLimiter.Level;
import com.marketplace.common.util.TimeProvider;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Hạn mức mỗi phút: 1-20 dùng LLM, 21-60 trả lời bằng luật, từ 61 bị chặn; phút mới đếm lại. */
class AiRateLimiterTest {

  private final TimeProvider clock = mock(TimeProvider.class);
  private final AiRateLimiter limiter = new AiRateLimiter(clock);

  @Test
  void levelsFollowTheCountWithinOneMinuteAndResetNextMinute() {
    when(clock.now()).thenReturn(Instant.parse("2026-10-26T10:00:05Z"));
    for (int i = 1; i <= 20; i++) {
      assertThat(limiter.acquire("ip:1.2.3.4")).isEqualTo(Level.AI);
    }
    for (int i = 21; i <= 60; i++) {
      assertThat(limiter.acquire("ip:1.2.3.4")).isEqualTo(Level.RULES);
    }
    assertThat(limiter.acquire("ip:1.2.3.4")).isEqualTo(Level.BLOCKED);
    // Người khác có bộ đếm riêng.
    assertThat(limiter.acquire("user:someone")).isEqualTo(Level.AI);

    // Sang phút mới: đếm lại từ đầu.
    when(clock.now()).thenReturn(Instant.parse("2026-10-26T10:01:00Z"));
    assertThat(limiter.acquire("ip:1.2.3.4")).isEqualTo(Level.AI);
  }
}
