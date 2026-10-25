package com.marketplace.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.marketplace.entity.Category;
import com.marketplace.entity.Product;
import com.marketplace.entity.User;
import com.marketplace.enums.Role;
import com.marketplace.enums.SellerStatus;
import com.marketplace.repository.ProductRepository;
import com.marketplace.support.TestSupport;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Đánh giá sản phẩm theo features/review/API_CONTRACT.md. Để tới order COMPLETED nhanh, test đặt thẳng
 * trạng thái order bằng SQL (luồng giao hàng đầy đủ đã có ở ShipmentApiTest).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewApiTest {

  @Autowired private MockMvc mvc;
  @Autowired private TestSupport support;
  @Autowired private ProductRepository products;
  @Autowired private JdbcTemplate jdbc;

  private User seller;
  private Product phone;

  @BeforeEach
  void setUp() {
    seller = support.user(Role.SELLER, SellerStatus.APPROVED);
    Category category = support.category();
    phone = support.product(seller, category, "Phone", "59.00", 10);
  }

  @Test
  void onlyACustomerWithACompletedOrderCanReview() throws Exception {
    User buyer = support.user(Role.CUSTOMER, null);
    review(buyer, phone, 5, "Great") // chưa mua
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("REVIEW_NOT_ELIGIBLE"));

    String order = buy(buyer, phone);
    review(buyer, phone, 5, "Great").andExpect(status().isForbidden()); // order còn PENDING
    setOrderStatus(order, "DELIVERED");
    review(buyer, phone, 5, "Great").andExpect(status().isForbidden()); // chưa xác nhận nhận hàng

    setOrderStatus(order, "COMPLETED");
    review(buyer, phone, 5, "  Great phone  ")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.productId").value(phone.getId().toString()))
        .andExpect(jsonPath("$.data.userId").value(buyer.getId().toString()))
        .andExpect(jsonPath("$.data.rating").value(5))
        .andExpect(jsonPath("$.data.comment").value("Great phone"));
    review(buyer, phone, 4, "Again")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("REVIEW_ALREADY_EXISTS"));
  }

  @Test
  void aCancelledLineDoesNotMakeTheCustomerEligible() throws Exception {
    User buyer = support.user(Role.CUSTOMER, null);
    String order = buy(buyer, phone);
    setOrderStatus(order, "COMPLETED");
    jdbc.update("update order_items set status = 'CANCELLED' where order_id = ?", UUID.fromString(order));
    review(buyer, phone, 5, "Great").andExpect(status().isForbidden());
  }

  @Test
  void reviewsUpdateTheProductRatingAndAreListedPublicly() throws Exception {
    for (int rating : new int[] {5, 4}) {
      User buyer = support.user(Role.CUSTOMER, null);
      setOrderStatus(buy(buyer, phone), "COMPLETED");
      review(buyer, phone, rating, "Rated " + rating).andExpect(status().isCreated());
    }

    String product = mvc.perform(get("/api/v1/products/" + phone.getId())).andReturn().getResponse().getContentAsString();
    assertThat(new BigDecimal(JsonPath.read(product, "$.data.ratingAverage").toString())).isEqualByComparingTo("4.50");
    assertThat((Integer) JsonPath.read(product, "$.data.reviewCount")).isEqualTo(2);

    mvc.perform(get("/api/v1/products/" + phone.getId() + "/reviews?size=1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].comment").value("Rated 4")); // mới nhất trước
  }

  @Test
  void invalidInputAndWrongActorsAreRejected() throws Exception {
    User buyer = support.user(Role.CUSTOMER, null);
    setOrderStatus(buy(buyer, phone), "COMPLETED");
    review(buyer, phone, 0, "x").andExpect(status().isBadRequest());
    review(buyer, phone, 6, "x").andExpect(status().isBadRequest());
    review(buyer, phone, 5, "   ").andExpect(status().isBadRequest());
    review(buyer, phone, 5, "x".repeat(2001)).andExpect(status().isBadRequest());

    review(seller, phone, 5, "Mine").andExpect(status().isForbidden()); // seller không viết đánh giá
    mvc.perform(post("/api/v1/products/" + phone.getId() + "/reviews")
            .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5,\"comment\":\"x\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void hiddenOrUnknownProductsAre404() throws Exception {
    Product hidden = support.product(seller, support.category(), "Hidden", "10.00", 1);
    hidden.setActive(false);
    products.save(hidden);
    mvc.perform(get("/api/v1/products/" + hidden.getId() + "/reviews")).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/products/" + UUID.randomUUID() + "/reviews")).andExpect(status().isNotFound());
    review(support.user(Role.CUSTOMER, null), hidden, 5, "x").andExpect(status().isNotFound());
  }

  // ---------- hàm hỗ trợ ----------

  /** Khách mua 1 cái (COD) và trả về id order. */
  private String buy(User buyer, Product product) throws Exception {
    String line = support.addToCart(mvc, buyer, product, 1);
    String order = support.send(mvc, post("/api/v1/orders"), buyer,
            "{\"cartItemIds\":[\"" + line + "\"],\"shippingAddress\":\"A\",\"paymentMethod\":\"COD\"}")
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return JsonPath.read(order, "$.data.id");
  }

  private void setOrderStatus(String orderId, String status) {
    jdbc.update("update orders set status = ? where id = ?", status, UUID.fromString(orderId));
  }

  private ResultActions review(User who, Product product, int rating, String comment) throws Exception {
    return support.send(mvc, post("/api/v1/products/" + product.getId() + "/reviews"), who,
        "{\"rating\":" + rating + ",\"comment\":\"" + comment + "\"}");
  }
}
