package com.marketplace.config;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/**
 * Bọc mỗi cache để lỗi của Redis (chết, timeout) không bao giờ làm request lỗi: chỉ ghi log rồi chạy tiếp
 * như không có cache. Đọc lỗi thì coi như không có trong cache; ghi hoặc xoá lỗi thì bỏ qua.
 *
 * <p>Vì sao không đủ với {@code CacheErrorHandler}: cache "transactionAware" hoãn việc ghi và xoá tới SAU
 * khi transaction commit, lúc đó lời gọi Redis đã ra ngoài {@code CacheInterceptor}, nên error handler không
 * bắt được. Lỗi sẽ thành 500 dù dữ liệu đã commit. Lớp này nằm BÊN TRONG lớp transactionAware nên bắt
 * được cả các lời gọi hoãn đó.
 */
@Slf4j
class ErrorTolerantCacheManager implements CacheManager {

  private final CacheManager target;
  private final Map<String, Cache> wrapped = new ConcurrentHashMap<>();

  ErrorTolerantCacheManager(CacheManager target) {
    this.target = target;
  }

  @Override
  public Cache getCache(String name) {
    Cache cache = target.getCache(name);
    return cache == null ? null : wrapped.computeIfAbsent(name, n -> new ErrorTolerantCache(cache));
  }

  @Override
  public Collection<String> getCacheNames() {
    return target.getCacheNames();
  }

  /** Mỗi thao tác gọi cache thật trong try/catch. */
  private static final class ErrorTolerantCache implements Cache {

    private final Cache cache;

    ErrorTolerantCache(Cache cache) {
      this.cache = cache;
    }

    @Override
    public String getName() {
      return cache.getName();
    }

    @Override
    public Object getNativeCache() {
      return cache.getNativeCache();
    }

    @Override
    public ValueWrapper get(Object key) {
      return safely("get", key, () -> cache.get(key), null);
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
      return safely("get", key, () -> cache.get(key, type), null);
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
      // Đọc lỗi thì tự tính giá trị (đọc PostgreSQL), không ghi lại vào cache.
      try {
        return cache.get(key, valueLoader);
      } catch (RuntimeException e) {
        warn("get", key, e);
        try {
          return valueLoader.call();
        } catch (Exception loaderError) {
          throw new ValueRetrievalException(key, valueLoader, loaderError);
        }
      }
    }

    @Override
    public void put(Object key, Object value) {
      safely("put", key, () -> {
        cache.put(key, value);
        return null;
      }, null);
    }

    @Override
    public void evict(Object key) {
      safely("evict", key, () -> {
        cache.evict(key);
        return null;
      }, null);
    }

    @Override
    public void clear() {
      safely("clear", "*", () -> {
        cache.clear();
        return null;
      }, null);
    }

    private <T> T safely(String operation, Object key, Supplier<T> call, T fallback) {
      try {
        return call.get();
      } catch (RuntimeException e) {
        warn(operation, key, e);
        return fallback;
      }
    }

    private void warn(String operation, Object key, RuntimeException e) {
      log.warn("Cache {} failed on {}::{}: {}", operation, cache.getName(), key, e.toString());
    }
  }
}
