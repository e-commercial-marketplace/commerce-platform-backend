package com.marketplace.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.marketplace.common.util.TimeProvider;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Sau 429 thì nghỉ đúng thời gian provider yêu cầu (tối đa 60 giây); lỗi khác không làm nghỉ. */
class ProviderCooldownTest {

  private static final Instant T0 = Instant.parse("2026-10-26T10:00:00Z");

  private final TimeProvider clock = mock(TimeProvider.class);
  private final ProviderCooldown cooldown = new ProviderCooldown(clock);

  @Test
  void waitsAsLongAsTheProviderAsksThenTriesAgain() {
    when(clock.now()).thenReturn(T0);
    cooldown.onFailure(new RuntimeException("HTTP 429 - Rate limit reached ... Please try again in 10.9s."));
    assertThat(cooldown.active()).isTrue();

    when(clock.now()).thenReturn(T0.plusSeconds(11)); // 10.9 làm tròn lên 11, cộng 1 giây cho chắc
    assertThat(cooldown.active()).isTrue();
    when(clock.now()).thenReturn(T0.plusSeconds(12));
    assertThat(cooldown.active()).isFalse();
  }

  @Test
  void usesDefaultOrMaximumWaitAndIgnoresOtherErrors() {
    when(clock.now()).thenReturn(T0);
    cooldown.onFailure(new RuntimeException("connection reset"));
    assertThat(cooldown.active()).isFalse();

    cooldown.onFailure(new RuntimeException("HTTP 429 Too Many Requests"));
    when(clock.now()).thenReturn(T0.plus(ProviderCooldown.DEFAULT_WAIT).minusSeconds(1));
    assertThat(cooldown.active()).isTrue();

    when(clock.now()).thenReturn(T0);
    cooldown.onFailure(new RuntimeException("429 rate limit, try again in 600s"));
    when(clock.now()).thenReturn(T0.plus(ProviderCooldown.MAX_WAIT));
    assertThat(cooldown.active()).isFalse();
  }
}
