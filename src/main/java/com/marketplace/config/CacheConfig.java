package com.marketplace.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.common.response.PageResponse;
import com.marketplace.dto.response.CategorySummaryResponse;
import com.marketplace.dto.response.ProductResponse;
import com.marketplace.dto.response.ProductSummaryResponse;
import com.marketplace.dto.response.SellerShopResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

/**
 * Spring Cache trên Redis cho các trang đọc công khai (architecture §5.4). Redis chỉ giữ bản sao; tắt
 * ({@code app.cache.enabled=false}) thì không cache gì, mọi lần đọc đi thẳng PostgreSQL.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

  /** Danh sách category công khai ({@code GET /categories}), key {@code 'all'}. */
  public static final String CATEGORIES = "categories";

  /** Trang shop công khai của seller ({@code GET /sellers/{id}}), key là id seller. */
  public static final String SELLER_SHOPS = "sellerShops";

  /** Chi tiết product công khai ({@code GET /products/{id}}), key là id product; xoá mỗi khi product đổi. */
  public static final String PRODUCTS = "products";

  /**
   * Một trang kết quả tìm product ({@code GET /products}), key là toàn bộ tham số tìm. Không xoá chủ động
   * (không biết trang nào chứa product nào), chỉ sống 60 giây.
   */
  public static final String PRODUCT_SEARCH = "productSearch";

  @Bean
  public CacheManager cacheManager(
      RedisConnectionFactory connectionFactory, @Value("${app.cache.enabled:false}") boolean enabled) {
    if (!enabled) {
      return new NoOpCacheManager(); // @Cacheable vẫn chạy nhưng không lưu gì
    }
    RedisCacheManager redis =
        RedisCacheManager.builder(connectionFactory)
            .withInitialCacheConfigurations(cacheConfigurations(new ObjectMapper()))
            .build();
    redis.afterPropertiesSet(); // không phải bean riêng nên tự khởi tạo các cache đã cấu hình
    // Hai lớp bọc, từ ngoài vào trong:
    // 1. TransactionAware: ghi và xoá cache chỉ sau khi transaction commit; rollback thì không đụng tới cache,
    //    và không request nào kịp đọc dữ liệu cũ rồi ghi lại vào cache trước lúc commit.
    // 2. ErrorTolerant: Redis lỗi (kể cả ở lần xoá chạy sau commit) chỉ ghi log, không làm request lỗi.
    return new TransactionAwareCacheManagerProxy(new ErrorTolerantCacheManager(redis));
  }

  /**
   * Cấu hình từng cache: thời gian sống và serializer JSON theo đúng kiểu dữ liệu (record không dùng được
   * JDK serialization). Không cache {@code null}.
   */
  static Map<String, RedisCacheConfiguration> cacheConfigurations(ObjectMapper mapper) {
    JavaType categoryList =
        mapper.getTypeFactory().constructCollectionType(List.class, CategorySummaryResponse.class);
    JavaType shop = mapper.getTypeFactory().constructType(SellerShopResponse.class);
    JavaType product = mapper.getTypeFactory().constructType(ProductResponse.class);
    // PageResponse<ProductSummaryResponse>: kiểu có tham số, phải mô tả cả kiểu bên trong cho Jackson.
    JavaType searchPage =
        mapper.getTypeFactory().constructParametricType(PageResponse.class, ProductSummaryResponse.class);
    return Map.of(
        CATEGORIES, json(mapper, categoryList, Duration.ofHours(1)),
        SELLER_SHOPS, json(mapper, shop, Duration.ofMinutes(10)),
        PRODUCTS, json(mapper, product, Duration.ofMinutes(30)),
        PRODUCT_SEARCH, json(mapper, searchPage, Duration.ofSeconds(60)));
  }

  private static RedisCacheConfiguration json(ObjectMapper mapper, JavaType type, Duration ttl) {
    return RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(ttl)
        .disableCachingNullValues()
        .serializeValuesWith(SerializationPair.fromSerializer(new Jackson2JsonRedisSerializer<>(mapper, type)));
  }

  /** Redis lỗi khi đọc, ghi hay xoá cache: chỉ ghi log rồi chạy tiếp như không có cache, request không lỗi. */
  @Override
  public CacheErrorHandler errorHandler() {
    return new LoggingCacheErrorHandler();
  }
}
