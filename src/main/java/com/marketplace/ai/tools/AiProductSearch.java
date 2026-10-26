package com.marketplace.ai.tools;

import com.marketplace.dto.request.ProductSearchRequest;
import com.marketplace.dto.response.AiProductQueryResponse;
import com.marketplace.dto.response.AiProductResponse;
import com.marketplace.dto.response.CategorySummaryResponse;
import com.marketplace.mapper.AiMapper;
import com.marketplace.service.CategoryService;
import com.marketplace.service.ProductService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
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
  private final CategoryService categoryService;
  private final AiMapper aiMapper;

  /** Lần tìm đã chạy (sau chuẩn hoá) và kết quả. */
  public record Result(AiProductQueryResponse query, List<AiProductResponse> products) {}

  /**
   * Tên các danh mục đang có product đang bán, theo thứ tự tên. Danh mục rỗng (mọi product đã ẩn) không được
   * giới thiệu cho khách.
   */
  public List<String> categoryNames() {
    var listed = productService.categoryIdsWithListedProducts();
    return categoryService.listAll().stream()
        .filter(c -> listed.contains(c.id()))
        .map(CategorySummaryResponse::name)
        .toList();
  }

  /**
   * Danh mục theo tên model hoặc người dùng đưa ra: trùng tên (không phân biệt hoa thường) trước, không có
   * thì danh mục có tên chứa cụm đó ("tai nghe" → "Tai nghe & Loa").
   */
  public Optional<CategorySummaryResponse> findCategory(String name) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    String wanted = name.trim().toLowerCase(Locale.ROOT);
    List<CategorySummaryResponse> all = categoryService.listAll();
    return all.stream()
        .filter(c -> c.name().toLowerCase(Locale.ROOT).equals(wanted))
        .findFirst()
        .or(() -> all.stream().filter(c -> c.name().toLowerCase(Locale.ROOT).contains(wanted)).findFirst());
  }

  /** @param category danh mục để lọc, {@code null} thì không lọc */
  public Result search(
      String text,
      CategorySummaryResponse category,
      BigDecimal minPrice,
      BigDecimal maxPrice,
      BigDecimal minRating,
      String sort) {
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
            .search(
                new ProductSearchRequest(
                    q, category == null ? null : category.id(), null, min, max, rating, order, 0, LIMIT))
            .content()
            .stream()
            .map(aiMapper::toProduct)
            .toList();
    String categoryName = category == null ? null : category.name();
    return new Result(new AiProductQueryResponse(q, categoryName, min, max, rating, order), products);
  }

  /** Bỏ số âm; giá luôn 2 chữ số thập phân như mọi số tiền của API (model gửi 1.0E7 thì ra 10000000.00). */
  private static BigDecimal nonNegative(BigDecimal value) {
    return value == null || value.signum() < 0 ? null : value.setScale(2, RoundingMode.HALF_UP);
  }
}
