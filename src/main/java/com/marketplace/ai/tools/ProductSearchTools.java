package com.marketplace.ai.tools;

import com.marketplace.dto.response.AiProductResponse;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Các tool chỉ đọc mà LLM được gọi: xem danh sách danh mục và tìm product công khai. Mỗi request tạo
 * một đối tượng mới, và đối tượng ghi lại lần tìm cuối, để response lấy {@code query} và {@code products}
 * từ dữ liệu thật thay vì từ lời model kể lại.
 */
public class ProductSearchTools {

  private final AiProductSearch productSearch;
  private AiProductSearch.Result lastResult;

  public ProductSearchTools(AiProductSearch productSearch) {
    this.productSearch = productSearch;
  }

  @Tool(
      description =
          "List the names of all product categories of the marketplace. Use it when the user asks what the "
              + "shop sells or wants to buy something without saying what.")
  public List<String> listCategories() {
    return productSearch.categoryNames();
  }

  @Tool(
      description =
          "Search listed marketplace products. Returns at most 5 products with name, price in VND, "
              + "rating and review count. Use it for every question about products, prices or stock. "
              + "All parameters are optional; with none it returns the top-rated products.")
  public List<AiProductResponse> searchProducts(
      @ToolParam(required = false, description = "Keywords from the product name, e.g. 'iphone', 'macbook'")
          String text,
      @ToolParam(required = false, description = "A category name exactly as returned by listCategories")
          String category,
      @ToolParam(required = false, description = "Minimum price in VND") Double minPrice,
      @ToolParam(required = false, description = "Maximum price in VND") Double maxPrice,
      @ToolParam(required = false, description = "Minimum average rating, 0 to 5") Double minRating,
      @ToolParam(required = false, description = "One of relevance, price_asc, price_desc, rating_desc")
          String sort) {
    // Tên danh mục lạ (model tự đặt) thì bỏ lọc danh mục thay vì trả rỗng; nếu chưa có từ khoá thì dùng
    // tên đó làm từ khoá.
    var found = productSearch.findCategory(category);
    String keywords = found.isEmpty() && (text == null || text.isBlank()) ? category : text;
    lastResult =
        productSearch.search(
            keywords, found.orElse(null), decimal(minPrice), decimal(maxPrice), decimal(minRating), sort);
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
