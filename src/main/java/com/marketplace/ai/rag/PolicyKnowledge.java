package com.marketplace.ai.rag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * RAG điều khoản (features/ai/README.md, "Policy RAG"): nạp các đoạn trong {@code rag/marketplace-terms.vi.md}
 * vào bảng {@code policy_chunks} (pgvector) và tìm các đoạn gần nghĩa nhất với câu hỏi.
 *
 * <p>Thiếu {@code AI_EMBEDDING_API_KEY} thì tắt hẳn: không gọi embedding, không đụng tới bảng (test dùng H2
 * không có pgvector cũng nhờ vậy mà chạy được).
 */
@Slf4j
@Component
public class PolicyKnowledge {

  /** File điều khoản trong classpath (src/main/resources). */
  static final String TERMS_FILE = "rag/marketplace-terms.vi.md";

  /** Số đoạn đưa cho model mỗi câu hỏi. */
  static final int TOP_K = 3;

  /** Một đoạn điều khoản: tiêu đề "##", dòng "Chủ đề:" và toàn bộ nội dung đoạn (kể cả tiêu đề). */
  record Chunk(String title, String topic, String content) {}

  private final EmbeddingModel embeddingModel;
  private final JdbcTemplate jdbcTemplate;
  private final TransactionTemplate transactionTemplate;
  private final boolean enabled;

  public PolicyKnowledge(
      EmbeddingModel embeddingModel,
      JdbcTemplate jdbcTemplate,
      TransactionTemplate transactionTemplate,
      @Value("${app.ai.embedding-api-key:}") String embeddingApiKey) {
    this.embeddingModel = embeddingModel;
    this.jdbcTemplate = jdbcTemplate;
    this.transactionTemplate = transactionTemplate;
    this.enabled = embeddingApiKey != null && !embeddingApiKey.isBlank();
  }

  /**
   * Chạy một lần khi ứng dụng đã khởi động xong: đọc file, embedding mọi đoạn rồi thay toàn bộ dòng cũ trong
   * bảng. Lỗi (provider, mạng, chưa có pgvector...) chỉ ghi log, ứng dụng vẫn chạy, trợ lý trả lời không có
   * trích đoạn.
   */
  @EventListener(ApplicationReadyEvent.class)
  public void loadTerms() {
    if (!enabled) {
      log.info("Policy RAG is off (AI_EMBEDDING_API_KEY is empty)");
      return;
    }
    try {
      List<Chunk> chunks = splitIntoChunks(readTermsFile());

      // Bước 1: embedding tất cả các đoạn trong một lần gọi provider.
      List<String> texts = new ArrayList<>();
      for (Chunk chunk : chunks) {
        texts.add(chunk.content());
      }
      List<float[]> vectors = embeddingModel.embed(texts);

      // Bước 2: xoá dữ liệu cũ rồi ghi lại, để đoạn đã bị xoá khỏi file không còn trong bảng. Chạy trong một
      // transaction: ghi lỗi giữa chừng thì quay lui, bảng vẫn giữ bản cũ đầy đủ.
      transactionTemplate.executeWithoutResult(
          status -> {
            jdbcTemplate.update("DELETE FROM policy_chunks");
            for (int i = 0; i < chunks.size(); i++) {
              Chunk chunk = chunks.get(i);
              jdbcTemplate.update(
                  "INSERT INTO policy_chunks (topic, title, content, embedding) VALUES (?, ?, ?, CAST(? AS vector))",
                  chunk.topic(),
                  chunk.title(),
                  chunk.content(),
                  toVectorText(vectors.get(i)));
            }
          });
      log.info("Policy RAG loaded {} chunks from {}", chunks.size(), TERMS_FILE);
    } catch (RuntimeException | IOException e) {
      log.warn("Could not load policy chunks, AI answers without policy excerpts: {}", e.toString());
    }
  }

  /**
   * {@link #TOP_K} đoạn điều khoản gần nghĩa nhất với câu hỏi. Trả danh sách rỗng khi RAG tắt hoặc gọi
   * embedding/DB lỗi, để câu hỏi vẫn được trả lời như khi không có RAG.
   */
  public List<String> findRelevant(String question) {
    if (!enabled) {
      return List.of();
    }
    try {
      String questionVector = toVectorText(embeddingModel.embed(question));
      // "<=>" là khoảng cách cosine của pgvector: càng nhỏ càng gần nghĩa.
      return jdbcTemplate.queryForList(
          "SELECT content FROM policy_chunks ORDER BY embedding <=> CAST(? AS vector) LIMIT ?",
          String.class,
          questionVector,
          TOP_K);
    } catch (RuntimeException e) {
      log.warn("Policy search failed, answering without policy excerpts: {}", e.toString());
      return List.of();
    }
  }

  private static String readTermsFile() throws IOException {
    return new ClassPathResource(TERMS_FILE).getContentAsString(StandardCharsets.UTF_8);
  }

  /**
   * Cắt file thành các đoạn: mỗi dòng bắt đầu bằng "## " mở một đoạn mới. Phần trước đoạn đầu tiên (tiêu đề
   * "#" và lời dẫn của file) bị bỏ qua.
   */
  static List<Chunk> splitIntoChunks(String markdown) {
    List<Chunk> chunks = new ArrayList<>();
    String title = null;
    String topic = "";
    StringBuilder content = new StringBuilder();

    for (String line : markdown.split("\\R")) {
      if (line.startsWith("## ")) {
        if (title != null) {
          chunks.add(new Chunk(title, topic, content.toString().trim()));
        }
        title = line.substring(3).trim();
        topic = "";
        content = new StringBuilder();
      }
      if (title == null) {
        continue; // chưa tới đoạn đầu tiên
      }
      if (line.startsWith("Chủ đề:")) {
        topic = line.substring("Chủ đề:".length()).trim();
      }
      content.append(line).append('\n');
    }
    if (title != null) {
      chunks.add(new Chunk(title, topic, content.toString().trim()));
    }
    return chunks;
  }

  /** Vector dạng chữ mà pgvector hiểu, ví dụ "[0.1,0.2,0.3]". */
  static String toVectorText(float[] vector) {
    StringBuilder text = new StringBuilder("[");
    for (int i = 0; i < vector.length; i++) {
      if (i > 0) {
        text.append(',');
      }
      text.append(vector[i]);
    }
    return text.append(']').toString();
  }
}
