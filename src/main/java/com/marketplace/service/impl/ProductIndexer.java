package com.marketplace.service.impl;

import com.marketplace.common.util.PgVector;
import com.marketplace.entity.Product;
import com.marketplace.repository.ProductRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tìm product theo nghĩa (features/catalog/README.md, "Semantic fallback"): giữ bảng {@code product_embeddings}
 * (pgvector) khớp với các product đang bán, và tìm các product gần nghĩa nhất với một câu tìm kiếm.
 *
 * <p>Thiếu {@code AI_EMBEDDING_API_KEY} thì tắt hẳn: không gọi embedding, không đụng tới bảng (test dùng H2
 * không có pgvector cũng nhờ vậy mà chạy được), giống {@code PolicyKnowledge}.
 */
@Slf4j
@Component
public class ProductIndexer {

  /** Số product gần nghĩa tối đa cho một câu tìm (cũng là giới hạn của phân trang trong bộ nhớ). */
  static final int LIMIT = 50;

  /**
   * Khoảng cách cosine tối đa để coi là "gần nghĩa" (0 = giống hệt, 2 = ngược nghĩa). Thiếu ngưỡng này thì
   * câu "bánh mì" vẫn ra chiếc điện thoại gần nhất. Đo trên seed với gemini-embedding-001: hàng đúng nghĩa nằm
   * khoảng 0.28–0.37; hàng shop không bán ("xe máy", "tủ lạnh", "áo sơ mi") ra món gần nhất ở 0.39–0.43. Hai vùng
   * chồng nhau một chút, 0.38 tách tốt nhất (đổi lại câu mơ hồ như "đồ để nghe nhạc", 0.380, ra rỗng).
   */
  static final double MAX_DISTANCE = 0.38;

  /**
   * Chỉ giữ product cách product gần nhất không quá chừng này. Hàng đúng nghĩa thường sát nhau ("đồng hồ thông
   * minh": 4 đồng hồ 0.28–0.29), hàng lạc đề tụt xa hơn (máy sấy Dyson 0.36), nên cắt được nhiễu mà ngưỡng tuyệt
   * đối không cắt được.
   */
  static final double MAX_GAP = 0.05;

  /** Số đoạn chữ tối đa trong một lần gọi embedding (Gemini nhận tối đa 100). */
  private static final int BATCH = 100;

  private final EmbeddingModel embeddingModel;
  private final JdbcTemplate jdbcTemplate;
  private final ProductRepository productRepository;
  private final boolean enabled;

  public ProductIndexer(
      EmbeddingModel embeddingModel,
      JdbcTemplate jdbcTemplate,
      ProductRepository productRepository,
      @Value("${app.ai.embedding-api-key:}") String embeddingApiKey) {
    this.embeddingModel = embeddingModel;
    this.jdbcTemplate = jdbcTemplate;
    this.productRepository = productRepository;
    this.enabled = embeddingApiKey != null && !embeddingApiKey.isBlank();
  }

  /**
   * Chạy 1 phút sau khi khởi động rồi cứ 10 phút một lần: chỉ embedding product đang bán có chữ đã đổi (so md5
   * với {@code content_hash}), nên thường không gọi provider lần nào. Dòng của product đã ẩn để nguyên: câu
   * tìm trong {@code ProductServiceImpl} vẫn lọc {@code active}. Lỗi chỉ ghi log, lần sau chạy lại.
   */
  @Scheduled(initialDelay = 1, fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
  public void sync() {
    if (!enabled) {
      return;
    }
    try {
      // Bước 1: hash đang lưu của từng product.
      Map<UUID, String> storedHashes = new HashMap<>();
      jdbcTemplate.query(
          "SELECT product_id, content_hash FROM product_embeddings",
          row -> {
            storedHashes.put(row.getObject("product_id", UUID.class), row.getString("content_hash"));
          });

      // Bước 2: chọn product mới hoặc có chữ đã đổi.
      List<UUID> ids = new ArrayList<>();
      List<String> texts = new ArrayList<>();
      List<String> hashes = new ArrayList<>();
      for (Product product : productRepository.findByActiveTrue()) {
        String text = textOf(product);
        String hash = md5(text);
        if (!hash.equals(storedHashes.get(product.getId()))) {
          ids.add(product.getId());
          texts.add(text);
          hashes.add(hash);
        }
      }

      // Bước 3: embedding từng lô rồi ghi đè (thêm mới hoặc cập nhật dòng cũ).
      for (int start = 0; start < ids.size(); start += BATCH) {
        int end = Math.min(start + BATCH, ids.size());
        List<float[]> vectors = embeddingModel.embed(texts.subList(start, end));
        for (int i = start; i < end; i++) {
          jdbcTemplate.update(
              "INSERT INTO product_embeddings (product_id, content_hash, embedding) VALUES (?, ?, CAST(? AS vector))"
                  + " ON CONFLICT (product_id) DO UPDATE"
                  + " SET content_hash = EXCLUDED.content_hash, embedding = EXCLUDED.embedding",
              ids.get(i),
              hashes.get(i),
              PgVector.toText(vectors.get(i - start)));
        }
      }
      if (!ids.isEmpty()) {
        log.info("Product embeddings synced {}", ids.size());
      }
    } catch (RuntimeException e) {
      log.warn("Could not sync product embeddings, will retry later: {}", e.toString());
    }
  }

  /**
   * Id các product gần nghĩa nhất với {@code text}, gần trước, tối đa {@link #LIMIT}. Có thể chứa product đã
   * ẩn, nên nơi gọi phải lọc lại. Trả danh sách rỗng khi tắt hoặc gọi embedding/DB lỗi, để tìm kiếm vẫn chạy
   * như khi không có tính năng này.
   */
  public List<UUID> findSimilar(String text) {
    if (!enabled) {
      return List.of();
    }
    try {
      String vector = PgVector.toText(embeddingModel.embed(text));
      // "<=>" là khoảng cách cosine của pgvector: càng nhỏ càng gần nghĩa. Truy vấn con tính khoảng cách một lần
      // và khoảng cách nhỏ nhất (MIN ... OVER () = min của mọi dòng, không gộp dòng).
      return jdbcTemplate.queryForList(
          "SELECT product_id FROM ("
              + "SELECT product_id, distance, MIN(distance) OVER () AS best FROM ("
              + "SELECT product_id, embedding <=> CAST(? AS vector) AS distance FROM product_embeddings) a"
              + ") d WHERE distance < ? AND distance <= best + ? ORDER BY distance LIMIT ?",
          UUID.class,
          vector,
          MAX_DISTANCE,
          MAX_GAP,
          LIMIT);
    } catch (RuntimeException e) {
      log.warn("Product semantic search failed, keeping the text search result: {}", e.toString());
      return List.of();
    }
  }

  /** Chữ đại diện cho một product: tên, danh mục, mô tả. */
  private static String textOf(Product product) {
    return product.getName() + "\n" + product.getCategory().getName() + "\n"
        + Objects.toString(product.getDescription(), "");
  }

  private static String md5(String text) {
    try {
      byte[] digest = MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("MD5 is always available in the JDK", e);
    }
  }
}
