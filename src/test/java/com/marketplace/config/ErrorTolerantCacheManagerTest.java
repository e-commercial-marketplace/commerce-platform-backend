package com.marketplace.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Redis chết thì cache chỉ ghi log: không đọc được thì đọc nguồn thật, không ghi/xoá được thì bỏ qua. Quan
 * trọng nhất là lần xoá cache bị hoãn tới sau commit (transactionAware) cũng không được ném lỗi ra request.
 */
class ErrorTolerantCacheManagerTest {

  private final Cache broken = mock(Cache.class);
  private final CacheManager redisDown = mock(CacheManager.class);

  ErrorTolerantCacheManagerTest() {
    RedisConnectionFailureException down = new RedisConnectionFailureException("down");
    when(redisDown.getCache("products")).thenReturn(broken);
    when(broken.getName()).thenReturn("products");
    when(broken.get(any())).thenThrow(down);
    when(broken.get(any(), any(Callable.class))).thenThrow(down);
    doThrow(down).when(broken).put(any(), any());
    doThrow(down).when(broken).evict(any());
  }

  @Test
  void readsFallBackToTheSourceAndWritesAreIgnored() {
    Cache cache = new ErrorTolerantCacheManager(redisDown).getCache("products");
    assertThat(cache.get("id")).isNull();
    assertThat(cache.get("id", () -> "from postgres")).isEqualTo("from postgres");
    assertThatCode(() -> cache.put("id", "value")).doesNotThrowAnyException();
    assertThatCode(() -> cache.evict("id")).doesNotThrowAnyException();
  }

  @Test
  void anEvictionDeferredUntilAfterCommitDoesNotThrow() {
    Cache cache =
        new TransactionAwareCacheManagerProxy(new ErrorTolerantCacheManager(redisDown)).getCache("products");
    TransactionSynchronizationManager.initSynchronization();
    try {
      cache.evict("id"); // trong transaction: chỉ đăng ký, chạy thật lúc afterCommit
      assertThatCode(
              () -> TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit))
          .doesNotThrowAnyException();
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }
}
