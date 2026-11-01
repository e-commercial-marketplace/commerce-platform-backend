package com.marketplace.controller;

import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketplace.entity.Category;
import com.marketplace.entity.Product;
import com.marketplace.entity.User;
import com.marketplace.enums.Role;
import com.marketplace.enums.SellerStatus;
import com.marketplace.repository.ProductRepository;
import com.marketplace.service.impl.ProductIndexer;
import com.marketplace.support.TestSupport;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Fallback tìm theo nghĩa của {@code GET /products}: chỉ dùng khi tìm theo chữ ra 0 kết quả, và vẫn áp mọi
 * bộ lọc lên các product gần nghĩa. {@link ProductIndexer} được giả lập vì H2 không có pgvector.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductSearchSemanticTest {

  private static final String QUERY = "máy tính xách tay";

  @Autowired private MockMvc mockMvc;
  @Autowired private TestSupport support;
  @Autowired private ProductRepository productRepository;
  @MockitoBean private ProductIndexer productIndexer;

  private Category category;
  private User seller;

  @BeforeEach
  void setUp() {
    category = support.category();
    seller = support.user(Role.SELLER, SellerStatus.APPROVED);
  }

  @Test
  void aTextMatchIsReturnedWithoutAskingForSimilarProducts() throws Exception {
    Product laptop = support.product(seller, category, "Laptop Dell XPS", "25000000.00", 3);

    mockMvc
        .perform(get("/api/v1/products").param("q", "dell xps").param("categoryId", category.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[*].id").value(contains(laptop.getId().toString())));
    verify(productIndexer, never()).findSimilar(anyString());
  }

  @Test
  void withoutATextMatchSimilarProductsComeInOrderAndStillPassTheFilters() throws Exception {
    Product cheap = support.product(seller, category, "Laptop Acer", "15000000.00", 3);
    Product pricey = support.product(seller, category, "Laptop MacBook", "30000000.00", 3);
    Product hidden = support.product(seller, category, "Laptop ẩn", "10000000.00", 3);
    hidden.setActive(false);
    productRepository.save(hidden);
    Product otherCategory = support.product(seller, support.category(), "Laptop khác", "12000000.00", 3);
    // Gần nghĩa nhất trước; có cả product đã ẩn và product khác danh mục, phải bị lọc đi.
    when(productIndexer.findSimilar(QUERY))
        .thenReturn(List.of(pricey.getId(), hidden.getId(), cheap.getId(), otherCategory.getId()));
    String categoryId = category.getId().toString();

    mockMvc
        .perform(get("/api/v1/products").param("q", QUERY).param("categoryId", categoryId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(
            jsonPath("$.data.content[*].id").value(contains(pricey.getId().toString(), cheap.getId().toString())));

    // Bộ lọc giá vẫn áp lên kết quả gần nghĩa.
    mockMvc
        .perform(get("/api/v1/products").param("q", QUERY).param("categoryId", categoryId).param("maxPrice", "20000000"))
        .andExpect(jsonPath("$.data.content[*].id").value(contains(cheap.getId().toString())));

    // Sắp theo giá thay cho thứ tự gần nghĩa; phân trang đúng tổng số.
    mockMvc
        .perform(
            get("/api/v1/products")
                .param("q", QUERY)
                .param("categoryId", categoryId)
                .param("sort", "price_asc")
                .param("size", "1")
                .param("page", "1"))
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.totalPages").value(2))
        .andExpect(jsonPath("$.data.content[*].id").value(contains(pricey.getId().toString())));
  }

  @Test
  void withoutTextOrSimilarMatchesTheListStaysEmpty() throws Exception {
    support.product(seller, category, "Laptop Acer", "15000000.00", 3);

    mockMvc
        .perform(get("/api/v1/products").param("q", "bánh mì").param("categoryId", category.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(0));
  }
}
