package com.marketplace.repository;

import com.marketplace.entity.Product;
import jakarta.persistence.criteria.Expression;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Các điều kiện lọc product dùng chung cho duyệt công khai (và sau này cho seller). */
public final class ProductSpecifications {

  private static final char LIKE_ESCAPE = '\\';

  private ProductSpecifications() {}

  /** Chỉ lấy product đang bán; product xoá mềm ({@code active = false}) bị ẩn khỏi đọc công khai. */
  public static Specification<Product> isActive() {
    return (root, query, cb) -> cb.isTrue(root.get("active"));
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

  /** Tìm không phân biệt hoa thường trong tên hoặc mô tả. */
  public static Specification<Product> textMatches(String text) {
    String pattern = likePattern(text);
    return (root, query, cb) ->
        cb.or(
            cb.like(cb.lower(root.get("name")), pattern, LIKE_ESCAPE),
            cb.like(cb.lower(root.<String>get("description")), pattern, LIKE_ESCAPE));
  }

  /**
   * Sắp theo độ liên quan: product khớp ở tên đứng trước product chỉ khớp ở mô tả, rồi tới rating
   * giảm dần và {@code id} tăng dần để thứ tự ổn định.
   *
   * <p>Spec này đặt {@code ORDER BY} trực tiếp, nên chỉ có tác dụng khi {@code Pageable} không kèm
   * sort (Spring Data sẽ ghi đè nếu có sort). Câu count không cần thứ tự nên bị bỏ qua.
   */
  public static Specification<Product> orderedByRelevance(String text) {
    String pattern = likePattern(text);
    return (root, query, cb) -> {
      if (query != null && !isCountQuery(query.getResultType())) {
        Expression<Integer> nameMatchRank =
            cb.<Integer>selectCase()
                .when(cb.like(cb.lower(root.get("name")), pattern, LIKE_ESCAPE), 0)
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

  /** Chuyển chuỗi người dùng thành pattern LIKE an toàn: ký tự {@code % _ \} được coi là chữ thường. */
  private static String likePattern(String text) {
    String escaped =
        text.toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    return "%" + escaped + "%";
  }
}
