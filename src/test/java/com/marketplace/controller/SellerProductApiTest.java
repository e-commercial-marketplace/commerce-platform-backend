package com.marketplace.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import com.marketplace.service.JwtService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Kiểm thử API product của seller theo contract catalog: tạo, sửa một phần, xoá mềm, đổi tồn kho,
 * chỉ cho seller đã APPROVED và chỉ trên product của chính mình.
 *
 * <p>Cách làm: mỗi test tự tạo seller/category riêng (tên ngẫu nhiên) và dựng token bằng
 * {@link JwtService}, nên không phụ thuộc dữ liệu của test khác trong cùng H2.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SellerProductApiTest {

  private static final String BASE = "/api/v1/seller/products";

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository users;
  @Autowired private CategoryRepository categories;
  @Autowired private ProductRepository products;
  @Autowired private JwtService jwtService;

  private User seller; // seller đã APPROVED dùng cho hầu hết test
  private Category category;

  @BeforeEach
  void setUp() {
    seller = user(Role.SELLER, SellerStatus.APPROVED);
    category = categories.save(Category.create("Cat-" + UUID.randomUUID()));
  }

  // ---------- tạo product ----------

  @Test
  void approvedSellerCreatesProductAndItAppearsPublicly() throws Exception {
    mvc.perform(
            post(BASE)
                .header(HttpHeaders.AUTHORIZATION, bearer(seller))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Headphone", "59.00", "12", category.getId().toString(), null)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.name").value("Headphone"))
        .andExpect(jsonPath("$.data.price").value("59.00"))
        .andExpect(jsonPath("$.data.stock").value(12))
        .andExpect(jsonPath("$.data.reviewCount").value(0))
        .andExpect(jsonPath("$.data.ratingAverage").value("0"))
        // sellerId phải lấy từ token chứ không phải từ client gửi lên.
        .andExpect(jsonPath("$.data.sellerId").value(seller.getId().toString()))
        .andExpect(jsonPath("$.data.category.id").value(category.getId().toString()));

    // Không gửi "active" nghĩa là đang bán, nên khách xem được ngay trong danh sách công khai.
    mvc.perform(get("/api/v1/products?categoryId=" + category.getId()))
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  @Test
  void productCreatedWithActiveFalseIsHiddenFromThePublic() throws Exception {
    String id =
        idOf(
            create(seller, body("Hidden", "10.00", "1", category.getId().toString(), "false"))
                .andExpect(status().isCreated()));

    mvc.perform(get("/api/v1/products/" + id)).andExpect(status().isNotFound());
  }

  @Test
  void invalidCreateRequestsReturn400() throws Exception {
    String cat = category.getId().toString();
    String[] invalidBodies = {
      body("Name", "0", "1", cat, null), // price phải > 0
      body("Name", "-5", "1", cat, null), // price âm
      body("Name", "59.999", "1", cat, null), // quá 2 chữ số thập phân
      body("Name", "10", "-1", cat, null), // stock âm
      body("", "10", "1", cat, null), // tên trống
      body("   ", "10", "1", cat, null), // tên toàn khoảng trắng
      body("Name", "10", "1", UUID.randomUUID().toString(), null), // categoryId không tồn tại
      "{\"price\":\"10\",\"stock\":1,\"categoryId\":\"" + cat + "\"}", // thiếu tên
      "{\"name\":\"Name\",\"stock\":1,\"categoryId\":\"" + cat + "\"}", // thiếu giá
      "{\"name\":\"Name\",\"price\":\"10\",\"categoryId\":\"" + cat + "\"}", // thiếu stock
      "{\"name\":\"Name\",\"price\":\"10\",\"stock\":1}" // thiếu categoryId
    };
    for (String invalid : invalidBodies) {
      create(seller, invalid)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
    // Không có yêu cầu nào ở trên được phép tạo ra product: danh mục này vẫn trống.
    mvc.perform(get("/api/v1/products?categoryId=" + category.getId()))
        .andExpect(jsonPath("$.data.totalElements").value(0));
  }

  // ---------- ai được gọi ----------

  @Test
  void onlyApprovedSellersCanUseTheSellerArea() throws Exception {
    String valid = body("Name", "10.00", "1", category.getId().toString(), null);

    // Không có token: 401.
    mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(valid))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));

    // Customer và admin sai role: 403 ngay ở luật URL.
    for (Role role : new Role[] {Role.CUSTOMER, Role.ADMIN}) {
      create(user(role, null), valid)
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    // Seller chưa được duyệt hoặc đã bị từ chối/khoá: 403 do service kiểm tra trạng thái trong DB.
    for (SellerStatus notApproved :
        new SellerStatus[] {SellerStatus.PENDING, SellerStatus.REJECTED, SellerStatus.SUSPENDED}) {
      create(user(Role.SELLER, notApproved), valid)
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }
  }

  @Test
  void sellerSuspendedAfterGettingATokenIsStillBlocked() throws Exception {
    String token = bearer(seller); // token cấp lúc seller còn APPROVED

    // Admin khoá seller sau đó. Token vẫn hợp lệ (chưa hết hạn), nhưng service tra DB nên phải chặn.
    seller.setSellerStatus(SellerStatus.SUSPENDED);
    users.save(seller);

    mvc.perform(
            post(BASE)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Name", "10.00", "1", category.getId().toString(), null)))
        .andExpect(status().isForbidden());
  }

  // ---------- quyền sở hữu (FR-CAT-06) ----------

  @Test
  void sellerCannotTouchAnotherSellersProduct() throws Exception {
    User other = user(Role.SELLER, SellerStatus.APPROVED);
    Product theirs = product(other, "Theirs", 5);
    String id = theirs.getId().toString();

    // Seller A thử sửa, xoá và đổi tồn kho product của seller B: cả ba đều bị 403.
    send(patch(BASE + "/" + id), seller, "{\"name\":\"Hacked\"}")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    send(patch(BASE + "/" + id + "/stock"), seller, "{\"stock\":999}")
        .andExpect(status().isForbidden());
    mvc.perform(delete(BASE + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(seller)))
        .andExpect(status().isForbidden());

    // Product của seller B phải còn nguyên: tên, tồn kho và trạng thái đang bán không đổi.
    Product unchanged = products.findById(theirs.getId()).orElseThrow();
    assertThat(unchanged.getName()).isEqualTo("Theirs");
    assertThat(unchanged.getStock()).isEqualTo(5);
    assertThat(unchanged.isActive()).isTrue();
  }

  @Test
  void unknownProductIs404() throws Exception {
    String missing = UUID.randomUUID().toString();

    send(patch(BASE + "/" + missing), seller, "{\"name\":\"X\"}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    send(patch(BASE + "/" + missing + "/stock"), seller, "{\"stock\":1}")
        .andExpect(status().isNotFound());
    mvc.perform(delete(BASE + "/" + missing).header(HttpHeaders.AUTHORIZATION, bearer(seller)))
        .andExpect(status().isNotFound());
  }

  // ---------- sửa một phần ----------

  @Test
  void patchChangesOnlyTheFieldsThatAreSent() throws Exception {
    Product mine = product(seller, "Original", 7);
    mine.setPrice(new BigDecimal("40.00"));
    products.save(mine);
    String id = mine.getId().toString();

    // Chỉ gửi tên: giá, tồn kho và danh mục phải giữ nguyên.
    send(patch(BASE + "/" + id), seller, "{\"name\":\"Renamed\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("Renamed"))
        .andExpect(jsonPath("$.data.price").value("40.00"))
        .andExpect(jsonPath("$.data.stock").value(7));

    // Gửi giá mới và đổi sang danh mục khác.
    Category other = categories.save(Category.create("Other-" + UUID.randomUUID()));
    send(patch(BASE + "/" + id), seller, "{\"price\":\"69.00\",\"categoryId\":\"" + other.getId() + "\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.price").value("69.00"))
        .andExpect(jsonPath("$.data.category.id").value(other.getId().toString()))
        .andExpect(jsonPath("$.data.name").value("Renamed"));
  }

  @Test
  void invalidPatchValuesReturn400() throws Exception {
    String id = product(seller, "Keep", 1).getId().toString();

    String[] invalidBodies = {
      "{\"price\":\"0\"}", // giá phải > 0
      "{\"price\":\"-1\"}",
      "{\"name\":\"   \"}", // tên không được toàn khoảng trắng
      "{\"categoryId\":\"" + UUID.randomUUID() + "\"}" // danh mục không tồn tại
    };
    for (String invalid : invalidBodies) {
      send(patch(BASE + "/" + id), seller, invalid)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
    assertThat(products.findById(UUID.fromString(id)).orElseThrow().getName()).isEqualTo("Keep");
  }

  // ---------- xoá mềm ----------

  @Test
  void deleteIsASoftDeleteAndTheSellerCanRelistIt() throws Exception {
    Product mine = product(seller, "Soft", 3);
    String id = mine.getId().toString();

    // DELETE trả 204 và không có body.
    mvc.perform(delete(BASE + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(seller)))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

    // Dòng dữ liệu vẫn còn (để đơn hàng và review cũ tham chiếu), chỉ bị ẩn khỏi khách.
    assertThat(products.findById(mine.getId()).orElseThrow().isActive()).isFalse();
    mvc.perform(get("/api/v1/products/" + id)).andExpect(status().isNotFound());

    // Seller vẫn sửa được product đã ẩn: đặt active=true để bán lại thì khách xem được trở lại.
    send(patch(BASE + "/" + id), seller, "{\"active\":true}").andExpect(status().isOk());
    mvc.perform(get("/api/v1/products/" + id)).andExpect(status().isOk());
  }

  // ---------- tồn kho ----------

  @Test
  void stockEndpointSetsTheNewStock() throws Exception {
    Product mine = product(seller, "Stock", 3);
    String id = mine.getId().toString();

    send(patch(BASE + "/" + id + "/stock"), seller, "{\"stock\":25}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.stock").value(25));
    assertThat(products.findById(mine.getId()).orElseThrow().getStock()).isEqualTo(25);

    // Tồn kho 0 hợp lệ (hết hàng); âm hoặc thiếu thì 400 và giữ nguyên số cũ.
    send(patch(BASE + "/" + id + "/stock"), seller, "{\"stock\":0}").andExpect(status().isOk());
    send(patch(BASE + "/" + id + "/stock"), seller, "{\"stock\":-1}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    send(patch(BASE + "/" + id + "/stock"), seller, "{}").andExpect(status().isBadRequest());
    assertThat(products.findById(mine.getId()).orElseThrow().getStock()).isEqualTo(0);
  }

  // ---------- hàm hỗ trợ ----------

  /** Tạo và lưu một người dùng với role (và trạng thái seller nếu có). */
  private User user(Role role, SellerStatus sellerStatus) {
    User user = User.register("u-" + UUID.randomUUID() + "@test.com", "hash", "Test", role);
    if (role == Role.SELLER) {
      user.setSellerStatus(sellerStatus); // User.register mặc định PENDING cho seller, ở đây ghi đè
    }
    return users.save(user);
  }

  /** Header Authorization với access token thật của người dùng. */
  private String bearer(User user) {
    return "Bearer " + jwtService.generateAccessToken(user);
  }

  /** Tạo và lưu một product đang bán thuộc seller. */
  private Product product(User owner, String name, int stock) {
    return products.save(
        Product.create(name, "desc", null, new BigDecimal("10.00"), stock, category, owner));
  }

  /** Dựng JSON tạo product; {@code active == null} thì bỏ trường active. */
  private static String body(String name, String price, String stock, String categoryId, String active) {
    return "{\"name\":\"%s\",\"description\":\"d\",\"price\":\"%s\",\"stock\":%s,\"categoryId\":\"%s\"%s}"
        .formatted(name, price, stock, categoryId, active == null ? "" : ",\"active\":" + active);
  }

  private ResultActions create(User who, String json) throws Exception {
    return send(post(BASE), who, json);
  }

  /** Gửi một request có body JSON kèm token của người dùng. */
  private ResultActions send(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
      User who,
      String json)
      throws Exception {
    return mvc.perform(
        request
            .header(HttpHeaders.AUTHORIZATION, bearer(who))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  private static String idOf(ResultActions result) throws Exception {
    return com.jayway.jsonpath.JsonPath.read(
        result.andReturn().getResponse().getContentAsString(), "$.data.id");
  }
}
