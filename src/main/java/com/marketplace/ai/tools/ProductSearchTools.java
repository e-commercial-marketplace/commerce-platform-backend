package com.marketplace.ai.tools;

import com.marketplace.dto.response.AiProductResponse;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Tool chỉ đọc mà LLM được gọi: tìm product công khai. Mỗi request tạo một đối tượng mới, và đối tượng
 * ghi lại lần tìm cuối, để response lấy {@code query} và {@code products} từ dữ liệu thật thay vì từ lời
 * model kể lại.
 */
public class ProductSearchTools {

  private final AiProductSearch productSearch;
  private AiProductSearch.Result lastResult;

  public ProductSearchTools(AiProductSearch productSearch) {
    this.productSearch = productSearch;
  }

  @Tool(
      description =
          "Search listed marketplace products. Returns at most 5 products with name, price in VND, "
              + "rating and review count. Use it for every question about products, prices or stock.")
  public List<AiProductResponse> searchProducts(
      @ToolParam(required = false, description = "Keywords from the product name, e.g. 'tai nghe'") String text,
      @ToolParam(required = false, description = "Minimum price in VND") Double minPrice,
      @ToolParam(required = false, description = "Maximum price in VND") Double maxPrice,
      @ToolParam(required = false, description = "Minimum average rating, 0 to 5") Double minRating,
      @ToolParam(required = false, description = "One of relevance, price_asc, price_desc, rating_desc")
          String sort) {
    lastResult = productSearch.search(text, null, decimal(minPrice), decimal(maxPrice), decimal(minRating), sort);
    return lastResult.products();
  }

  /** Lần tìm cuối; {@code null} nếu model không gọi tool. */
  public AiProductSearch.Result lastResult() {
    return lastResult;
  }

  private static BigDecimal decimal(Double value) {
    return value == null ? null : BigDecimal.valueOf(value);
  }
}
