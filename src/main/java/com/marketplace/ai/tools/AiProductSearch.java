package com.marketplace.ai.tools;

import com.marketplace.dto.request.ProductSearchRequest;
import com.marketplace.dto.response.AiProductQueryResponse;
import com.marketplace.dto.response.AiProductResponse;
import com.marketplace.mapper.AiMapper;
import com.marketplace.service.ProductService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Tìm product cho trợ lý AI, dùng chung cho tool của LLM và bộ trả lời bằng luật. Tham số đến từ model
 * hoặc từ câu người dùng nên không tin được: chuẩn hoá trước khi gọi {@link ProductService#search}
 * (đúng FR-AI-05, không gọi thẳng repository), và chỉ lấy tối đa {@link #LIMIT} product đang bán.
 */
@Component
@RequiredArgsConstructor
public class AiProductSearch {

  /** Số product tối đa trong một câu trả lời. */
  public static final int LIMIT = 5;

  private static final Set<String> SORTS = Set.of("relevance", "price_asc", "price_desc", "rating_desc");
  private static final BigDecimal MAX_RATING = BigDecimal.valueOf(5);

  private final ProductService productService;
  private final AiMapper aiMapper;

  /** Lần tìm đã chạy (sau chuẩn hoá) và kết quả. */
  public record Result(AiProductQueryResponse query, List<AiProductResponse> products) {}

  public Result search(
      String text, UUID categoryId, BigDecimal minPrice, BigDecimal maxPrice, BigDecimal minRating, String sort) {
    // Bước 1: chuẩn hoá. Giá âm bỏ đi; min > max thì đổi chỗ (search sẽ báo 400); rating kẹp trong 0..5;
    // sort lạ quy về mặc định.
    String q = text == null || text.isBlank() ? null : text.trim();
    BigDecimal min = nonNegative(minPrice);
    BigDecimal max = nonNegative(maxPrice);
    if (min != null && max != null && min.compareTo(max) > 0) {
      BigDecimal swap = min;
      min = max;
      max = swap;
    }
    BigDecimal rating = minRating == null ? null : minRating.max(BigDecimal.ZERO).min(MAX_RATING);
    String order = sort != null && SORTS.contains(sort.trim()) ? sort.trim() : null;

    // Bước 2: tìm trang đầu, tối đa LIMIT product đang bán.
    List<AiProductResponse> products =
        productService
            .search(new ProductSearchRequest(q, categoryId, null, min, max, rating, order, 0, LIMIT))
            .content()
            .stream()
            .map(aiMapper::toProduct)
            .toList();
    return new Result(new AiProductQueryResponse(q, min, max, rating, order), products);
  }

  private static BigDecimal nonNegative(BigDecimal value) {
    return value == null || value.signum() < 0 ? null : value;
  }
}
