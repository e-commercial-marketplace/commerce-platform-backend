package com.marketplace.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketplace.entity.Category;
import com.marketplace.entity.Product;
import com.marketplace.entity.User;
import com.marketplace.enums.Role;
import com.marketplace.enums.SellerStatus;
import com.marketplace.repository.ProductRepository;
import com.marketplace.support.TestSupport;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Kiểm thử trang shop công khai {@code GET /api/v1/sellers/{id}}: khách xem được shop của seller đã
 * duyệt (tên shop là tên seller) mà không cần token, và không lộ thông tin của người không phải seller
 * hoặc seller chưa được duyệt.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SellerShopApiTest {

  @Autowired private MockMvc mvc;
  @Autowired private TestSupport support;
  @Autowired private ProductRepository products;

  @Test
  void anyoneCanViewAnApprovedSellersShop() throws Exception {
    User seller = support.user(Role.SELLER, SellerStatus.APPROVED, "Anh Hoang Store");
    Category category = support.category();

    // Hai product đang bán và một product đã ẩn: productCount chỉ đếm hai cái đang bán.
    product(seller, category, "A", true);
    product(seller, category, "B", true);
    product(seller, category, "Hidden", false);
    // Product của seller khác không được tính vào shop này.
    product(support.user(Role.SELLER, SellerStatus.APPROVED, "Other"), category, "Other's", true);

    // Không gửi token: trang shop là công khai.
    mvc.perform(get("/api/v1/sellers/" + seller.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(seller.getId().toString()))
        // Tên shop chính là tên của seller.
        .andExpect(jsonPath("$.data.name").value("Anh Hoang Store"))
        .andExpect(jsonPath("$.data.productCount").value(2))
        // Không lộ email hay mật khẩu băm của seller.
        .andExpect(jsonPath("$.data.email").doesNotExist())
        .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
  }

  @Test
  void shopOfASellerWithoutProductsHasCountZero() throws Exception {
    User seller = support.user(Role.SELLER, SellerStatus.APPROVED, "Empty Shop");

    mvc.perform(get("/api/v1/sellers/" + seller.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.productCount").value(0));
  }

  @Test
  void notApprovedSellersAndNonSellersAreNotFound() throws Exception {
    // Seller chưa duyệt, bị từ chối hoặc bị khoá: coi như shop không tồn tại.
    for (SellerStatus status :
        new SellerStatus[] {SellerStatus.PENDING, SellerStatus.REJECTED, SellerStatus.SUSPENDED}) {
      User seller = support.user(Role.SELLER, status, "Hidden Shop");
      mvc.perform(get("/api/v1/sellers/" + seller.getId()))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    // Customer và admin không phải seller: cùng một lỗi 404, người ngoài không phân biệt được.
    for (Role role : new Role[] {Role.CUSTOMER, Role.ADMIN}) {
      User other = support.user(role, null, "Not A Seller");
      mvc.perform(get("/api/v1/sellers/" + other.getId())).andExpect(status().isNotFound());
    }

    // Id không tồn tại.
    mvc.perform(get("/api/v1/sellers/" + UUID.randomUUID())).andExpect(status().isNotFound());
  }

  @Test
  void malformedSellerIdIs400() throws Exception {
    mvc.perform(get("/api/v1/sellers/not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }

  // ---------- hàm hỗ trợ ----------

  /** Tạo và lưu một product thuộc seller, đang bán hoặc đã ẩn. */
  private void product(User owner, Category category, String name, boolean active) {
    Product product =
        Product.create(name, "d", null, new BigDecimal("10.00"), 1, category, owner);
    product.setActive(active);
    products.save(product);
  }
}
