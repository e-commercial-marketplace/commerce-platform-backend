package com.marketplace.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
import com.marketplace.support.TestSupport;
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
 * {@link TestSupport}, nên không phụ thuộc dữ liệu của test khác trong cùng H2.
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
  @Autowired private TestSupport support;

  private User seller; // seller đã APPROVED dùng cho hầu hết test
  private Category category;

  @BeforeEach
  void setUp() {
    seller = support.user(Role.SELLER, SellerStatus.APPROVED);
    category = support.category();
  }

  // ---------- tạo product ----------

  @Test
  void approvedSellerCreatesProductAndItAppearsPublicly() throws Exception {
    mvc.perform(
            post(BASE)
                .header(HttpHeaders.AUTHORIZATION, support.bearer(seller))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Headphone", "59.00", "12", category.getId().toString(), null)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.name").value("Headphone"))
        .andExpect(jsonPath("$.data.price").value("59.00"))
        .andExpect(jsonPath("$.data.stock").value(12))
        .andExpect(jsonPath("$.data.reviewCount").value(0))
        .andExpect(jsonPath("$.data.active").value(true))
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
      create(support.user(role, null), valid)
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    // Seller chưa được duyệt hoặc đã bị từ chối/khoá: 403 do service kiểm tra trạng thái trong DB.
    for (SellerStatus notApproved :
        new SellerStatus[] {SellerStatus.PENDING, SellerStatus.REJECTED, SellerStatus.SUSPENDED}) {
      create(support.user(Role.SELLER, notApproved), valid)
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }
  }

  @Test
  void sellerSuspendedAfterGettingATokenIsStillBlocked() throws Exception {
    String token = support.bearer(seller); // token cấp lúc seller còn APPROVED

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
    User other = support.user(Role.SELLER, SellerStatus.APPROVED);
    Product theirs = product(other, "Theirs", 5);
    String id = theirs.getId().toString();

    // Seller A thử sửa, xoá và đổi tồn kho product của seller B: cả ba đều bị 403.
    support.send(mvc, patch(BASE + "/" + id), seller, "{\"name\":\"Hacked\"}")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    support.send(mvc, patch(BASE + "/" + id + "/stock"), seller, "{\"stock\":999}")
        .andExpect(status().isForbidden());
    mvc.perform(delete(BASE + "/" + id).header(HttpHeaders.AUTHORIZATION, support.bearer(seller)))
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

    support.send(mvc, patch(BASE + "/" + missing), seller, "{\"name\":\"X\"}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    support.send(mvc, patch(BASE + "/" + missing + "/stock"), seller, "{\"stock\":1}")
        .andExpect(status().isNotFound());
    mvc.perform(delete(BASE + "/" + missing).header(HttpHeaders.AUTHORIZATION, support.bearer(seller)))
        .andExpect(status().isNotFound());
  }

  // ---------- sản phẩm đang bán phải có ảnh ----------

  @Test
  void anActiveProductNeedsAnImageButAHiddenOneDoesNot() throws Exception {
    String noImage = "{\"name\":\"NoImage\",\"price\":\"10.00\",\"stock\":1,\"categoryId\":\"%s\"%s}";
    String cat = category.getId().toString();

    // Đang bán (mặc định) mà thiếu ảnh hoặc ảnh rỗng: 400, không tạo gì.
    create(seller, noImage.formatted(cat, "")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    create(seller, noImage.formatted(cat, ",\"imageUrl\":\"   \"")).andExpect(status().isBadRequest());

    // Tạo ở trạng thái ẩn thì không cần ảnh.
    String id = idOf(create(seller, noImage.formatted(cat, ",\"active\":false")).andExpect(status().isCreated()));

    // Bật bán lại khi chưa có ảnh: 400. Gửi kèm ảnh thì được.
    support.send(mvc, patch(BASE + "/" + id), seller, "{\"active\":true}").andExpect(status().isBadRequest());
    support.send(mvc, patch(BASE + "/" + id), seller, "{\"active\":true,\"imageUrl\":\"https://example.com/x.jpg\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.active").value(true));

    // Xoá ảnh của product đang bán (ảnh rỗng): 400, product giữ nguyên ảnh cũ.
    support.send(mvc, patch(BASE + "/" + id), seller, "{\"imageUrl\":\"\"}").andExpect(status().isBadRequest());
    assertThat(products.findById(UUID.fromString(id)).orElseThrow().getImageUrl())
        .isEqualTo("https://example.com/x.jpg");
  }

  // ---------- sửa một phần ----------

  @Test
  void patchChangesOnlyTheFieldsThatAreSent() throws Exception {
    Product mine = product(seller, "Original", 7);
    mine.setPrice(new BigDecimal("40.00"));
    products.save(mine);
    String id = mine.getId().toString();

    // Chỉ gửi tên: giá, tồn kho và danh mục phải giữ nguyên.
    support.send(mvc, patch(BASE + "/" + id), seller, "{\"name\":\"Renamed\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("Renamed"))
        .andExpect(jsonPath("$.data.price").value("40.00"))
        .andExpect(jsonPath("$.data.stock").value(7));

    // Gửi giá mới và đổi sang danh mục khác.
    Category other = categories.save(Category.builder().name("Other-" + UUID.randomUUID()).build());
    support.send(mvc, patch(BASE + "/" + id), seller, "{\"price\":\"69.00\",\"categoryId\":\"" + other.getId() + "\"}")
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
      support.send(mvc, patch(BASE + "/" + id), seller, invalid)
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
    mvc.perform(delete(BASE + "/" + id).header(HttpHeaders.AUTHORIZATION, support.bearer(seller)))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

    // Dòng dữ liệu vẫn còn (để đơn hàng và review cũ tham chiếu), chỉ bị ẩn khỏi khách.
    assertThat(products.findById(mine.getId()).orElseThrow().isActive()).isFalse();
    mvc.perform(get("/api/v1/products/" + id)).andExpect(status().isNotFound());

    // Seller vẫn sửa được product đã ẩn: đặt active=true để bán lại thì khách xem được trở lại.
    support.send(mvc, patch(BASE + "/" + id), seller, "{\"active\":true}").andExpect(status().isOk());
    mvc.perform(get("/api/v1/products/" + id)).andExpect(status().isOk());
  }

  // ---------- tồn kho ----------

  @Test
  void stockEndpointSetsTheNewStock() throws Exception {
    Product mine = product(seller, "Stock", 3);
    String id = mine.getId().toString();

    support.send(mvc, patch(BASE + "/" + id + "/stock"), seller, "{\"stock\":25}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.stock").value(25));
    assertThat(products.findById(mine.getId()).orElseThrow().getStock()).isEqualTo(25);

    // Tồn kho 0 hợp lệ (hết hàng); âm hoặc thiếu thì 400 và giữ nguyên số cũ.
    support.send(mvc, patch(BASE + "/" + id + "/stock"), seller, "{\"stock\":0}").andExpect(status().isOk());
    support.send(mvc, patch(BASE + "/" + id + "/stock"), seller, "{\"stock\":-1}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    support.send(mvc, patch(BASE + "/" + id + "/stock"), seller, "{}").andExpect(status().isBadRequest());
    assertThat(products.findById(mine.getId()).orElseThrow().getStock()).isEqualTo(0);
  }

  // ---------- danh sách và chi tiết của chính seller ----------

  @Test
  void sellerSearchIgnoresAccentsAndStaysOnOwnProducts() throws Exception {
    User other = support.user(Role.SELLER, SellerStatus.APPROVED);
    product(seller, "Đồng hồ của tôi", 1);
    product(other, "Đồng hồ của người khác", 1);

    list("q=dong ho").andExpect(jsonPath("$.data.content[*].name", contains("Đồng hồ của tôi")));
  }

  @Test
  void sellerListsOnlyOwnProductsIncludingHiddenOnes() throws Exception {
    User other = support.user(Role.SELLER, SellerStatus.APPROVED);
    product(seller, "Visible", 1);
    hide(product(seller, "Hidden", 1));
    product(other, "NotMine", 1); // của seller khác, không được xuất hiện

    // Mặc định thấy cả đang bán lẫn đã ẩn, nhưng không thấy product của seller khác.
    list("")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.content[*].name", containsInAnyOrder("Visible", "Hidden")));

    // active=false chỉ lấy product đã ẩn (và trường active phản ánh đúng).
    list("active=false")
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].name").value("Hidden"))
        .andExpect(jsonPath("$.data.content[0].active").value(false));

    // active=true chỉ lấy product đang bán.
    list("active=true")
        .andExpect(jsonPath("$.data.content[*].name", contains("Visible")))
        .andExpect(jsonPath("$.data.content[0].active").value(true));

    // q tìm theo tên, không phân biệt hoa thường.
    list("q=VIS").andExpect(jsonPath("$.data.content[*].name", contains("Visible")));
  }

  @Test
  void sellerListIsOrderedByNameAndPaginated() throws Exception {
    product(seller, "B", 1);
    product(seller, "C", 1);
    product(seller, "A", 1);

    list("size=2&page=0")
        .andExpect(jsonPath("$.data.totalElements").value(3))
        .andExpect(jsonPath("$.data.totalPages").value(2))
        .andExpect(jsonPath("$.data.content[*].name", contains("A", "B")));
    list("size=2&page=1").andExpect(jsonPath("$.data.content[*].name", contains("C")));
  }

  @Test
  void sellerListRejectsInvalidParamsAndUnapprovedCallers() throws Exception {
    for (String invalid : new String[] {"size=0", "size=101", "page=-1", "active=maybe"}) {
      list(invalid)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // Không có token: 401.
    mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    // Sai role: 403. Seller chưa duyệt: 403.
    for (User caller :
        new User[] {
          support.user(Role.CUSTOMER, null),
          support.user(Role.ADMIN, null),
          support.user(Role.SELLER, SellerStatus.PENDING)
        }) {
      mvc.perform(get(BASE).header(HttpHeaders.AUTHORIZATION, support.bearer(caller)))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }
  }

  @Test
  void sellerCanViewOwnHiddenProductButNotAnotherSellers() throws Exception {
    Product hidden = hide(product(seller, "Mine hidden", 4));
    Product theirs = product(support.user(Role.SELLER, SellerStatus.APPROVED), "Theirs", 1);

    // Product của chính mình, kể cả đã ẩn: thấy đầy đủ, active=false.
    mvc.perform(get(BASE + "/" + hidden.getId()).header(HttpHeaders.AUTHORIZATION, support.bearer(seller)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("Mine hidden"))
        .andExpect(jsonPath("$.data.stock").value(4))
        .andExpect(jsonPath("$.data.active").value(false));

    // Product của seller khác: 403. Không tồn tại: 404.
    mvc.perform(get(BASE + "/" + theirs.getId()).header(HttpHeaders.AUTHORIZATION, support.bearer(seller)))
        .andExpect(status().isForbidden());
    mvc.perform(get(BASE + "/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, support.bearer(seller)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
  }

  // ---------- hàm hỗ trợ ----------

  /** GET danh sách product của chính {@code seller} với chuỗi query cho trước. */
  private ResultActions list(String query) throws Exception {
    return mvc.perform(get(BASE + "?" + query).header(HttpHeaders.AUTHORIZATION, support.bearer(seller)));
  }

  /** Ẩn một product (xoá mềm) rồi lưu lại. */
  private Product hide(Product product) {
    product.setActive(false);
    return products.save(product);
  }

  /** Tạo và lưu một product đang bán thuộc seller. */
  private Product product(User owner, String name, int stock) {
    return support.product(owner, category, name, "10.00", stock);
  }

  /** Dựng JSON tạo product (luôn kèm ảnh); {@code active == null} thì bỏ trường active. */
  private static String body(String name, String price, String stock, String categoryId, String active) {
    return ("{\"name\":\"%s\",\"description\":\"d\",\"imageUrl\":\"https://example.com/p.jpg\","
            + "\"price\":\"%s\",\"stock\":%s,\"categoryId\":\"%s\"%s}")
        .formatted(name, price, stock, categoryId, active == null ? "" : ",\"active\":" + active);
  }

  private ResultActions create(User who, String json) throws Exception {
    return support.send(mvc, post(BASE), who, json);
  }

  private static String idOf(ResultActions result) throws Exception {
    return com.jayway.jsonpath.JsonPath.read(
        result.andReturn().getResponse().getContentAsString(), "$.data.id");
  }
}
