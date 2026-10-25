package com.marketplace.repository;

import com.marketplace.entity.CartItem;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Cache đọc các dòng giỏ hàng trong Redis (architecture §5.4). PostgreSQL {@code cart_items} vẫn là nguồn
 * sự thật: cache chỉ là bản sao, mất đi thì đọc lại từ PostgreSQL.
 *
 * <p>Mỗi customer một Redis Hash {@code cart:<userId>}: field = id dòng giỏ, value =
 * {@code productId|quantity|createdAt}. Không lưu giá hay tồn kho, nên product luôn đọc mới từ PostgreSQL.
 *
 * <p>Redis là tuỳ chọn: tắt ({@code app.cache.enabled=false}) hoặc lỗi thì mọi hàm coi như cache
 * trống, chỉ ghi log cảnh báo, không bao giờ làm hỏng request.
 */
@Slf4j
@Component
public class CartCache {

  /** Thời gian sống của bản sao; cũng là giới hạn trên cho dữ liệu cũ trong rủi ro đã chấp nhận. */
  static final Duration TTL = Duration.ofMinutes(30);

  private static final String SEPARATOR = "|";

  private final StringRedisTemplate redis;
  private final boolean enabled;

  public CartCache(StringRedisTemplate redis, @Value("${app.cache.enabled:false}") boolean enabled) {
    this.redis = redis;
    this.enabled = enabled;
  }

  /** Một dòng giỏ đã cache: chỉ phần thuộc về giỏ, không có dữ liệu product. */
  public record CachedLine(UUID id, UUID productId, int quantity, Instant createdAt) {}

  /** Các dòng giỏ đã cache; rỗng khi chưa cache (cache miss), khi cache tắt hoặc khi Redis lỗi. */
  public Optional<List<CachedLine>> get(UUID userId) {
    if (!enabled) {
      return Optional.empty();
    }
    try {
      Map<Object, Object> entries = redis.opsForHash().entries(key(userId));
      if (entries.isEmpty()) {
        return Optional.empty(); // giỏ rỗng không được cache, nên cũng là miss
      }
      return Optional.of(
          entries.entrySet().stream().map(e -> parse((String) e.getKey(), (String) e.getValue())).toList());
    } catch (RuntimeException e) {
      // Redis chết, timeout hoặc dữ liệu lạ: coi như miss, request đọc PostgreSQL.
      log.warn("Cart cache read failed for user {}: {}", userId, e.toString());
      return Optional.empty();
    }
  }

  /** Ghi bản sao giỏ vừa đọc từ PostgreSQL. Giỏ rỗng thì không ghi (Redis không có Hash rỗng). */
  public void put(UUID userId, List<CartItem> items) {
    if (!enabled || items.isEmpty()) {
      return;
    }
    Map<String, String> fields = new HashMap<>();
    for (CartItem item : items) {
      fields.put(
          item.getId().toString(),
          item.getProduct().getId() + SEPARATOR + item.getQuantity() + SEPARATOR + item.getCreatedAt());
    }
    try {
      String key = key(userId);
      redis.opsForHash().putAll(key, fields);
      // shortcut: putAll và expire là hai lệnh; chết giữa chừng thì key thiếu TTL tới lần sửa giỏ kế tiếp
      // (khi đó key bị xoá). Dùng MULTI nếu cần chặt hơn.
      redis.expire(key, TTL);
    } catch (RuntimeException e) {
      log.warn("Cart cache write failed for user {}: {}", userId, e.toString());
    }
  }

  /**
   * Xoá bản sao sau khi transaction hiện tại commit, để lần đọc sau lấy dữ liệu mới từ PostgreSQL.
   * Transaction rollback thì giữ nguyên cache (PostgreSQL cũng không đổi). Không có transaction thì xoá ngay.
   */
  public void evictAfterCommit(UUID userId) {
    if (!enabled) {
      return;
    }
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      evict(userId);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            evict(userId);
          }
        });
  }

  private void evict(UUID userId) {
    try {
      redis.delete(key(userId));
    } catch (RuntimeException e) {
      // Không xoá được thì bản cũ còn tối đa TTL; PostgreSQL vẫn đúng và checkout luôn đọc PostgreSQL.
      log.warn("Cart cache evict failed for user {}: {}", userId, e.toString());
    }
  }

  static String key(UUID userId) {
    return "cart:" + userId;
  }

  private static CachedLine parse(String field, String value) {
    String[] parts = value.split("\\" + SEPARATOR);
    return new CachedLine(
        UUID.fromString(field), UUID.fromString(parts[0]), Integer.parseInt(parts[1]), Instant.parse(parts[2]));
  }
}
