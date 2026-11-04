package com.marketplace.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;
import org.junit.jupiter.api.Test;

/** Bảng bỏ dấu viết tay: đối chiếu với kết quả tách dấu của Unicode để không sai vị trí ký tự nào. */
class LikePatternTest {

  @Test
  void everyAccentedLetterMapsToItsBaseLetter() {
    assertThat(LikePattern.VIETNAMESE_PLAIN).hasSameSizeAs(LikePattern.VIETNAMESE_ACCENTED);
    String stripped =
        Normalizer.normalize(LikePattern.VIETNAMESE_ACCENTED, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replace('đ', 'd');
    assertThat(LikePattern.VIETNAMESE_PLAIN).isEqualTo(stripped);
  }

  @Test
  void removesAccentsCaseAndCombiningMarksAndKeepsWildcardsEscaped() {
    assertThat(LikePattern.containsIgnoringAccents("ĐỒNG Hồ")).isEqualTo("%dong ho%");
    assertThat(LikePattern.containsIgnoringAccents(Normalizer.normalize("đồng hồ", Normalizer.Form.NFD)))
        .isEqualTo("%dong ho%");
    assertThat(LikePattern.containsIgnoringAccents("50%_é")).isEqualTo("%50\\%\\_e%");
  }
}
