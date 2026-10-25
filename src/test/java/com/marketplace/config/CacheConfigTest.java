package com.marketplace.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.dto.response.CategorySummaryResponse;
import com.marketplace.dto.response.SellerShopResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

/**
 * Giá trị cache phải ghi ra JSON và đọc lại đúng kiểu record ban đầu. Bản Redis trước dùng JDK
 * serialization nên không lưu được record; test này giữ cho lỗi đó không quay lại.
 */
class CacheConfigTest {

  private final Map<String, RedisCacheConfiguration> configs =
      CacheConfig.cacheConfigurations(new ObjectMapper());

  @Test
  void theCategoryListRoundTripsAsJson() {
    List<CategorySummaryResponse> list =
        List.of(new CategorySummaryResponse(UUID.randomUUID(), "Điện thoại"));
    assertThat(roundTrip(CacheConfig.CATEGORIES, list)).isEqualTo(list);
    assertThat(configs.get(CacheConfig.CATEGORIES).getTtlFunction().getTimeToLive(list, list))
        .isEqualTo(Duration.ofHours(1));
  }

  @Test
  void theShopPageRoundTripsAsJson() {
    SellerShopResponse shop = new SellerShopResponse(UUID.randomUUID(), "Shop A", 12);
    assertThat(roundTrip(CacheConfig.SELLER_SHOPS, shop)).isEqualTo(shop);
    assertThat(configs.get(CacheConfig.SELLER_SHOPS).getTtlFunction().getTimeToLive(shop, shop))
        .isEqualTo(Duration.ofMinutes(10));
  }

  private Object roundTrip(String cache, Object value) {
    var pair = configs.get(cache).getValueSerializationPair();
    ByteBuffer bytes = pair.write(value);
    return pair.read(bytes);
  }
}
