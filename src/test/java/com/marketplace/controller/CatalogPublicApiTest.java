package com.marketplace.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketplace.entity.Category;
import com.marketplace.entity.Product;
import com.marketplace.entity.User;
import com.marketplace.enums.Role;
import com.marketplace.enums.SellerStatus;
import com.marketplace.repository.CategoryRepository;
import com.marketplace.repository.ProductRepository;
import com.marketplace.repository.UserRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Kiểm thử API catalog công khai: duyệt/tìm/lọc/sắp xếp/phân trang product, xem chi tiết và danh
 * sách category, không cần token. Mỗi test tạo category riêng và lọc theo {@code categoryId} nên
 * không phụ thuộc dữ liệu của test khác trong cùng H2.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogPublicApiTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private ProductRepository productRepository;
  @Autowired private UserRepository userRepository;

  private Category category;
  private User seller;

  @BeforeEach
  void setUp() {
    category = categoryRepository.save(Category.create("Cat-" + UUID.randomUUID()));
    seller = User.register("seller-" + UUID.randomUUID() + "@test.com", "hash", "Seller", Role.SELLER);
    seller.setSellerStatus(SellerStatus.APPROVED);
    seller = userRepository.save(seller);
  }

  // ---------- GET /products ----------

  @Test
  void listIsPublicAndMatchesContractShape() throws Exception {
    Product p = save("Headphone", "Wireless headphone", "59.00", 12, "4.50", 99, true);

    list("categoryId=" + category.getId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(20))
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.totalPages").value(1))
        .andExpect(jsonPath("$.data.content[0].id").value(p.getId().toString()))
        .andExpect(jsonPath("$.data.content[0].name").value("Headphone"))
        .andExpect(jsonPath("$.data.content[0].price").value("59.00"))
        .andExpect(jsonPath("$.data.content[0].stock").value(12))
        .andExpect(jsonPath("$.data.content[0].ratingAverage").value("4.50"))
        .andExpect(jsonPath("$.data.content[0].reviewCount").value(99))
        .andExpect(jsonPath("$.data.content[0].category.id").value(category.getId().toString()))
        .andExpect(jsonPath("$.data.content[0].category.name").value(category.getName()))
        .andExpect(jsonPath("$.data.content[0].sellerId").value(seller.getId().toString()));
  }

  @Test
  void inactiveProductsAreHidden() throws Exception {
    save("Visible", "d", "10.00", 1, "0.00", 0, true);
    save("Hidden", "d", "10.00", 1, "0.00", 0, false);

    list("categoryId=" + category.getId())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].name").value("Visible"));
  }

  @Test
  void textSearchMatchesNameOrDescriptionIgnoringCase() throws Exception {
    save("Red Shoes", "leather", "10.00", 1, "0.00", 0, true);
    save("Blue Bag", "goes well with SHOES", "10.00", 1, "0.00", 0, true);
    save("Green Hat", "plain", "10.00", 1, "0.00", 0, true);

    list("categoryId=" + category.getId() + "&q=shoes")
        .andExpect(jsonPath("$.data.totalElements").value(2));
  }

  @Test
  void relevanceRanksNameMatchBeforeDescriptionMatch() throws Exception {
    // Mô tả khớp nhưng rating cao hơn; tên khớp phải vẫn đứng trước.
    save("Blue Bag", "pairs with shoes", "10.00", 1, "5.00", 0, true);
    save("Red Shoes", "leather", "10.00", 1, "1.00", 0, true);

    list("categoryId=" + category.getId() + "&q=shoes")
        .andExpect(jsonPath("$.data.content[*].name", contains("Red Shoes", "Blue Bag")));
  }

  @Test
  void likeWildcardsInSearchAreTreatedAsPlainText() throws Exception {
    save("100% Cotton", "shirt", "10.00", 1, "0.00", 0, true);
    save("Plain Shirt", "shirt", "10.00", 1, "0.00", 0, true);

    list("categoryId=" + category.getId() + "&q=%") // MockMvc tự mã hoá thành q=%25
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].name").value("100% Cotton"));
  }

  @Test
  void filtersByPriceRangeAndMinRating() throws Exception {
    save("Cheap", "d", "10.00", 1, "2.00", 0, true);
    save("Mid", "d", "50.00", 1, "4.00", 0, true);
    save("Pricey", "d", "100.00", 1, "4.80", 0, true);

    String c = "categoryId=" + category.getId();
    list(c + "&minPrice=50&maxPrice=100")
        .andExpect(jsonPath("$.data.content[*].name", contains("Pricey", "Mid"))); // rating_desc
    list(c + "&minRating=4.5").andExpect(jsonPath("$.data.content[*].name", contains("Pricey")));
    list(c + "&minPrice=50&maxPrice=50")
        .andExpect(jsonPath("$.data.content[*].name", contains("Mid"))); // khoảng giá bao gồm cận
  }

  @Test
  void sortsByPriceAndRating() throws Exception {
    save("A", "d", "30.00", 1, "3.00", 0, true);
    save("B", "d", "10.00", 1, "5.00", 0, true);
    save("C", "d", "20.00", 1, "4.00", 0, true);

    String c = "categoryId=" + category.getId();
    list(c + "&sort=price_asc").andExpect(jsonPath("$.data.content[*].name", contains("B", "C", "A")));
    list(c + "&sort=price_desc").andExpect(jsonPath("$.data.content[*].name", contains("A", "C", "B")));
    list(c + "&sort=rating_desc").andExpect(jsonPath("$.data.content[*].name", contains("B", "C", "A")));
    list(c).andExpect(jsonPath("$.data.content[*].name", contains("B", "C", "A"))); // mặc định rating_desc
  }

  @Test
  void paginatesResults() throws Exception {
    for (int i = 1; i <= 5; i++) {
      save("P" + i, "d", i + "0.00", 1, "0.00", 0, true);
    }
    String c = "categoryId=" + category.getId() + "&sort=price_asc&size=2";

    list(c + "&page=0")
        .andExpect(jsonPath("$.data.totalElements").value(5))
        .andExpect(jsonPath("$.data.totalPages").value(3))
        .andExpect(jsonPath("$.data.content[*].name", contains("P1", "P2")));
    list(c + "&page=2")
        .andExpect(jsonPath("$.data.page").value(2))
        .andExpect(jsonPath("$.data.content[*].name", contains("P5")));
  }

  @Test
  void invalidQueryParamsReturn400() throws Exception {
    String[] bad = {
      "sort=cheapest",
      "minRating=6",
      "minRating=-1",
      "minPrice=-5",
      "minPrice=100&maxPrice=10",
      "size=0",
      "size=101",
      "page=-1",
      "page=abc",
      "categoryId=not-a-uuid"
    };
    for (String query : bad) {
      list(query)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
  }

  // ---------- GET /products/{id} ----------

  @Test
  void detailReturnsActiveProduct() throws Exception {
    Product p = save("Headphone", "Wireless", "59.00", 12, "4.50", 99, true);

    mockMvc
        .perform(get("/api/v1/products/" + p.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(p.getId().toString()))
        .andExpect(jsonPath("$.data.price").value("59.00"))
        .andExpect(jsonPath("$.data.category.name").value(category.getName()))
        .andExpect(jsonPath("$.data.sellerId").value(seller.getId().toString()));
  }

  @Test
  void detailOfInactiveOrUnknownProductReturns404() throws Exception {
    Product hidden = save("Hidden", "d", "10.00", 1, "0.00", 0, false);

    for (UUID id : new UUID[] {hidden.getId(), UUID.randomUUID()}) {
      mockMvc
          .perform(get("/api/v1/products/" + id))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }
  }

  // ---------- GET /categories ----------

  @Test
  void categoriesArePublicAndSortedByName() throws Exception {
    Category first = categoryRepository.save(Category.create("AAA-" + UUID.randomUUID()));
    Category last = categoryRepository.save(Category.create("zzz-" + UUID.randomUUID()));

    mockMvc
        .perform(get("/api/v1/categories"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[*].id", hasItem(first.getId().toString())))
        .andExpect(jsonPath("$.data[*].name", hasItem(last.getName())))
        .andExpect(jsonPath("$.data[0].name").isNotEmpty());
  }

  // ---------- helpers ----------

  private ResultActions list(String query) throws Exception {
    return mockMvc.perform(get("/api/v1/products?" + query));
  }

  private Product save(
      String name,
      String description,
      String price,
      int stock,
      String rating,
      int reviewCount,
      boolean active) {
    Product product =
        Product.create(name, description, null, new BigDecimal(price), stock, category, seller);
    product.setRatingAverage(new BigDecimal(rating));
    product.setReviewCount(reviewCount);
    product.setActive(active);
    return productRepository.save(product);
  }
}
