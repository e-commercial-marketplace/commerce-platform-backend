package com.marketplace.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.common.response.PageResponse;
import com.marketplace.dto.response.CategorySummaryResponse;
import com.marketplace.dto.response.ProductResponse;
import com.marketplace.dto.response.ProductSummaryResponse;
import java.math.BigDecimal;
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

  @Test
  void productDetailAndSearchPagesRoundTripAsJson() {
    CategorySummaryResponse category = new CategorySummaryResponse(UUID.randomUUID(), "Laptop");
    ProductResponse product =
        new ProductResponse(
            UUID.randomUUID(), "MacBook", "d", "https://example.com/p.jpg", new BigDecimal("19990000.00"), 5,
            new BigDecimal("4.50"), 2, true, category, UUID.randomUUID());
    assertThat(roundTrip(CacheConfig.PRODUCTS, product)).isEqualTo(product);

    // PageResponse có kiểu tham số: phải đọc lại đúng ProductSummaryResponse bên trong, không phải Map.
    PageResponse<ProductSummaryResponse> page =
        new PageResponse<>(
            List.of(
                new ProductSummaryResponse(
                    product.id(), "MacBook", "d", "https://example.com/p.jpg", new BigDecimal("19990000.00"), 5,
                    new BigDecimal("4.50"), 2, true, category, product.sellerId())),
            0, 20, 1, 1);
    assertThat(roundTrip(CacheConfig.PRODUCT_SEARCH, page)).isEqualTo(page);
    assertThat(configs.get(CacheConfig.PRODUCT_SEARCH).getTtlFunction().getTimeToLive(page, page))
        .isEqualTo(Duration.ofSeconds(60));
  }

  private Object roundTrip(String cache, Object value) {
    var pair = configs.get(cache).getValueSerializationPair();
    ByteBuffer bytes = pair.write(value);
    return pair.read(bytes);
  }
}
