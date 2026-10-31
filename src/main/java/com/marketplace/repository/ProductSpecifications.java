package com.marketplace.repository;

import com.marketplace.common.util.LikePattern;
import com.marketplace.entity.Product;
import jakarta.persistence.criteria.Expression;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Các điều kiện lọc product dùng chung cho duyệt công khai (và sau này cho seller). */
public final class ProductSpecifications {

  private ProductSpecifications() {}

  /** Lọc theo trạng thái hiển thị: {@code true} chỉ lấy product đang bán, {@code false} chỉ lấy product đã ẩn. */
  public static Specification<Product> activeIs(boolean active) {
    return (root, query, cb) -> cb.equal(root.get("active"), active);
  }

  /** Chỉ lấy product của một seller (dùng cho trang shop công khai và danh sách của chính seller). */
  public static Specification<Product> soldBy(UUID sellerId) {
    return (root, query, cb) -> cb.equal(root.get("seller").get("id"), sellerId);
  }

  public static Specification<Product> inCategory(UUID categoryId) {
    return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
  }

  public static Specification<Product> priceAtLeast(BigDecimal min) {
    return (root, query, cb) -> cb.greaterThanOrEqualTo(root.<BigDecimal>get("price"), min);
  }

  public static Specification<Product> priceAtMost(BigDecimal max) {
    return (root, query, cb) -> cb.lessThanOrEqualTo(root.<BigDecimal>get("price"), max);
  }

  public static Specification<Product> ratingAtLeast(BigDecimal min) {
    return (root, query, cb) -> cb.greaterThanOrEqualTo(root.<BigDecimal>get("ratingAverage"), min);
  }

  /** Chỉ lấy các product có id trong danh sách (kết quả tìm theo nghĩa của {@code ProductIndexer}). */
  public static Specification<Product> idIn(Collection<UUID> ids) {
    return (root, query, cb) -> root.get("id").in(ids);
  }

  /** Tìm không phân biệt hoa thường trong tên hoặc mô tả. */
  public static Specification<Product> textMatches(String text) {
    String pattern = LikePattern.contains(text);
    return (root, query, cb) ->
        cb.or(
            cb.like(cb.lower(root.get("name")), pattern, LikePattern.ESCAPE),
            cb.like(cb.lower(root.<String>get("description")), pattern, LikePattern.ESCAPE));
  }

  /**
   * Sắp theo độ liên quan: product khớp ở tên đứng trước product chỉ khớp ở mô tả, rồi tới rating
   * giảm dần và {@code id} tăng dần để thứ tự ổn định.
   *
   * <p>Spec này đặt {@code ORDER BY} trực tiếp, nên chỉ có tác dụng khi {@code Pageable} không kèm
   * sort (Spring Data sẽ ghi đè nếu có sort). Câu count không cần thứ tự nên bị bỏ qua.
   */
  public static Specification<Product> orderedByRelevance(String text) {
    String pattern = LikePattern.contains(text);
    return (root, query, cb) -> {
      if (query != null && !isCountQuery(query.getResultType())) {
        Expression<Integer> nameMatchRank =
            cb.<Integer>selectCase()
                .when(cb.like(cb.lower(root.get("name")), pattern, LikePattern.ESCAPE), 0)
                .otherwise(1);
        query.orderBy(
            cb.asc(nameMatchRank),
            cb.desc(root.get("ratingAverage")),
            cb.asc(root.get("id")));
      }
      return cb.conjunction();
    };
  }

  private static boolean isCountQuery(Class<?> resultType) {
    return Long.class.equals(resultType) || long.class.equals(resultType);
  }
}
