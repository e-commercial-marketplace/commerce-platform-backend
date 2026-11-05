package com.marketplace.ai.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.marketplace.dto.response.AiProductResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Model chỉ nhận phần cần đọc của product, còn response của API vẫn đủ mọi trường. */
class ProductSearchToolsTest {

  @Test
  void theModelGetsOnlyWhatItReadsAndTheResponseKeepsEveryField() {
    AiProductResponse product =
        new AiProductResponse(
            UUID.randomUUID(),
            "Tai nghe",
            "https://example.com/tai-nghe.png",
            new BigDecimal("100.00"),
            5,
            new BigDecimal("4.50"),
            2,
            UUID.randomUUID());
    AiProductSearch search = mock(AiProductSearch.class);
    when(search.findCategory(any())).thenReturn(Optional.empty());
    when(search.search(any(), any(), any(), any(), any(), any()))
        .thenReturn(new AiProductSearch.Result(null, List.of(product)));
    ProductSearchTools tools = new ProductSearchTools(search);

    List<ProductSearchTools.ProductForModel> forModel =
        tools.searchProducts("tai nghe", null, null, null, null, null);

    assertThat(forModel)
        .containsExactly(
            new ProductSearchTools.ProductForModel(
                "Tai nghe", new BigDecimal("100.00"), 5, new BigDecimal("4.50"), 2));
    assertThat(tools.lastResult().products()).containsExactly(product);
  }
}
