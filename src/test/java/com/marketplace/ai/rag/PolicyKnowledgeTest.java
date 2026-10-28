package com.marketplace.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.marketplace.ai.rag.PolicyKnowledge.Chunk;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Cắt file điều khoản thành đoạn, đổi vector sang dạng chữ của pgvector, và tắt hẳn khi thiếu key. */
class PolicyKnowledgeTest {

  @Test
  void theTermsFileSplitsIntoOneChunkPerSectionWithItsTopic() throws Exception {
    String markdown = new ClassPathResource(PolicyKnowledge.TERMS_FILE).getContentAsString(StandardCharsets.UTF_8);

    List<Chunk> chunks = PolicyKnowledge.splitIntoChunks(markdown);

    assertThat(chunks).hasSize(17);
    assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.topic()).isNotBlank());
    assertThat(chunks.get(0).title()).isEqualTo("Giới thiệu sàn");
    assertThat(chunks.get(0).topic()).isEqualTo("chung");
    // Đoạn chứa luôn tiêu đề, và không lẫn lời dẫn ở đầu file.
    assertThat(chunks.get(0).content()).startsWith("## Giới thiệu sàn").doesNotContain("Nguồn:");
  }

  @Test
  void textBeforeTheFirstSectionIsIgnoredAndTheLastSectionIsKept() {
    String markdown = "# Tiêu đề\nLời dẫn\n\n## A\nChủ đề: một\nNội dung A\n\n## B\nNội dung B";

    List<Chunk> chunks = PolicyKnowledge.splitIntoChunks(markdown);

    assertThat(chunks).containsExactly(
        new Chunk("A", "một", "## A\nChủ đề: một\nNội dung A"),
        new Chunk("B", "", "## B\nNội dung B"));
  }

  @Test
  void aVectorIsWrittenInPgvectorFormat() {
    assertThat(PolicyKnowledge.toVectorText(new float[] {0.5f, -1.0f, 2.25f})).isEqualTo("[0.5,-1.0,2.25]");
  }

  @Test
  void withoutAnEmbeddingKeyNothingIsCalled() {
    EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    PolicyKnowledge knowledge =
        new PolicyKnowledge(embeddingModel, jdbcTemplate, mock(TransactionTemplate.class), "");

    knowledge.loadTerms();

    assertThat(knowledge.findRelevant("thanh toán thế nào")).isEmpty();
    verifyNoInteractions(embeddingModel, jdbcTemplate);
  }
}
