package com.marketplace.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.marketplace.entity.Category;
import com.marketplace.entity.User;
import com.marketplace.enums.Role;
import com.marketplace.enums.SellerStatus;
import com.marketplace.repository.UserRepository;
import com.marketplace.support.TestSupport;
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
 * Kiểm thử admin quản lý user và duyệt seller theo {@code features/user/API_CONTRACT.md}: danh sách có
 * lọc, tạo, sửa, vô hiệu hoá, approve/suspend; admin không tự khoá mình; chỉ ADMIN được gọi.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserApiTest {

  private static final String USERS = "/api/v1/admin/users";
  private static final String SELLERS = "/api/v1/admin/sellers";
  private static final String PASSWORD = "Passw0rd!";

  @Autowired private MockMvc mvc;
  @Autowired private TestSupport support;
  @Autowired private UserRepository users;

  private User admin;

  @BeforeEach
  void setUp() {
    admin = support.user(Role.ADMIN, null);
  }

  // ---------- phân quyền ----------

  @Test
  void onlyAdminCanUseUserManagement() throws Exception {
    User target = support.user(Role.CUSTOMER, null);
    String[] urls = {USERS, USERS + "/" + target.getId()};

    // Không token: 401.
    for (String url : urls) {
      mvc.perform(get(url)).andExpect(status().isUnauthorized());
    }
    mvc.perform(patch(SELLERS + "/" + target.getId() + "/approve")).andExpect(status().isUnauthorized());

    // Customer và seller sai role: 403 FORBIDDEN cho cả đọc, ghi và duyệt seller.
    for (Role role : new Role[] {Role.CUSTOMER, Role.SELLER}) {
      User caller = support.user(role, SellerStatus.APPROVED);
      for (String url : urls) {
        mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, support.bearer(caller)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
      }
      support.send(mvc, post(USERS), caller, "{}").andExpect(status().isForbidden());
      mvc.perform(
              patch(SELLERS + "/" + target.getId() + "/approve")
                  .header(HttpHeaders.AUTHORIZATION, support.bearer(caller)))
          .andExpect(status().isForbidden());
    }
  }

  // ---------- danh sách và chi tiết ----------

  @Test
  void listFiltersByRoleAndSearchAndNeverExposesPasswordHash() throws Exception {
    String tag = UUID.randomUUID().toString().substring(0, 8);
    User seller = support.user(Role.SELLER, SellerStatus.PENDING, "Shop " + tag);
    support.user(Role.CUSTOMER, null, "Shop " + tag); // cùng tên nhưng là customer

    // Lọc theo role: chỉ seller. Tìm theo tên (không phân biệt hoa thường).
    mvc.perform(get(USERS).param("role", "SELLER").param("search", "SHOP " + tag).header(HttpHeaders.AUTHORIZATION, support.bearer(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].id").value(seller.getId().toString()))
        .andExpect(jsonPath("$.data.content[0].sellerStatus").value("PENDING"))
        .andExpect(jsonPath("$.data.content[0].enabled").value(true))
        .andExpect(jsonPath("$.data.content[0].passwordHash").doesNotExist());

    // Tìm theo email.
    mvc.perform(get(USERS).param("search", seller.getEmail()).header(HttpHeaders.AUTHORIZATION, support.bearer(admin)))
        .andExpect(jsonPath("$.data.totalElements").value(1));

    // Ký tự LIKE trong từ khoá chỉ là chữ thường, không phải ký tự đại diện.
    mvc.perform(get(USERS).param("search", "%").header(HttpHeaders.AUTHORIZATION, support.bearer(admin)))
        .andExpect(jsonPath("$.data.totalElements").value(0));
  }

  @Test
  void listPagesAndRejectsInvalidParams() throws Exception {
    String tag = UUID.randomUUID().toString().substring(0, 8);
    for (int i = 0; i < 3; i++) {
      support.user(Role.CUSTOMER, null, "Page " + tag);
    }

    mvc.perform(get(USERS).param("search", "Page " + tag).param("size", "2").header(HttpHeaders.AUTHORIZATION, support.bearer(admin)))
        .andExpect(jsonPath("$.data.content.length()").value(2))
        .andExpect(jsonPath("$.data.totalElements").value(3))
        .andExpect(jsonPath("$.data.totalPages").value(2))
        .andExpect(jsonPath("$.data.page").value(0));

    for (String query : new String[] {"page=-1", "size=0", "size=101", "role=BOSS"}) {
      mvc.perform(get(USERS + "?" + query).header(HttpHeaders.AUTHORIZATION, support.bearer(admin)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
  }

  @Test
  void getReturnsTheUserOr404Or400() throws Exception {
    User customer = support.user(Role.CUSTOMER, null, "Detail");

    mvc.perform(get(USERS + "/" + customer.getId()).header(HttpHeaders.AUTHORIZATION, support.bearer(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.email").value(customer.getEmail()))
        .andExpect(jsonPath("$.data.name").value("Detail"))
        .andExpect(jsonPath("$.data.role").value("CUSTOMER"))
        .andExpect(jsonPath("$.data.sellerStatus").doesNotExist()) // null bị bỏ khỏi JSON hoặc là null
        .andExpect(jsonPath("$.data.createdAt").isNotEmpty());

    mvc.perform(get(USERS + "/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, support.bearer(admin)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    mvc.perform(get(USERS + "/not-a-uuid").header(HttpHeaders.AUTHORIZATION, support.bearer(admin)))
        .andExpect(status().isBadRequest());
  }

  // ---------- tạo ----------

  @Test
  void adminCreatesUsersOfAnyRoleAndTheyCanLogIn() throws Exception {
    String email = "Staff-" + UUID.randomUUID() + "@Test.com";

    support.send(mvc, post(USERS), admin, create(email, "ADMIN", null))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.email").value(email.toLowerCase())) // email được chuẩn hoá
        .andExpect(jsonPath("$.data.role").value("ADMIN"))
        .andExpect(jsonPath("$.data.enabled").value(true))
        .andExpect(jsonPath("$.data.password").doesNotExist())
        .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

    login(email, PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("$.data.user.role").value("ADMIN"));
  }

  @Test
  void createdSellerStartsPendingAndDisabledUserCannotLogIn() throws Exception {
    String sellerEmail = email();
    support.send(mvc, post(USERS), admin, create(sellerEmail, "SELLER", null))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.sellerStatus").value("PENDING"));

    String disabledEmail = email();
    support.send(mvc, post(USERS), admin, create(disabledEmail, "CUSTOMER", false))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.enabled").value(false));
    login(disabledEmail, PASSWORD).andExpect(status().isUnauthorized());
  }

  @Test
  void invalidOrDuplicateCreateReturns400() throws Exception {
    String existing = email();
    support.send(mvc, post(USERS), admin, create(existing, "CUSTOMER", null)).andExpect(status().isCreated());

    String[] invalidBodies = {
      create(existing, "CUSTOMER", null), // trùng y hệt
      create(existing.toUpperCase(), "CUSTOMER", null), // trùng khác hoa thường
      create("not-an-email", "CUSTOMER", null),
      create(email(), "BOSS", null), // role không tồn tại
      "{\"email\":\"" + email() + "\",\"password\":\"" + PASSWORD + "\",\"name\":\"X\"}", // thiếu role
      "{\"email\":\"" + email() + "\",\"password\":\"short\",\"name\":\"X\",\"role\":\"CUSTOMER\"}", // mật khẩu ngắn
      "{\"email\":\"" + email() + "\",\"password\":\"" + PASSWORD + "\",\"name\":\" \",\"role\":\"CUSTOMER\"}" // tên trống
    };
    for (String invalid : invalidBodies) {
      support.send(mvc, post(USERS), admin, invalid)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
  }

  // ---------- sửa ----------

  @Test
  void updateChangesNameAndRoleWithConsistentSellerStatus() throws Exception {
    User user = support.user(Role.CUSTOMER, null, "Old");
    String url = USERS + "/" + user.getId();

    // Đổi tên (cắt khoảng trắng).
    support.send(mvc, patch(url), admin, "{\"name\":\"  New Name  \"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("New Name"))
        .andExpect(jsonPath("$.data.role").value("CUSTOMER"));

    // Customer thành SELLER mà không nói trạng thái: PENDING.
    support.send(mvc, patch(url), admin, "{\"role\":\"SELLER\"}")
        .andExpect(jsonPath("$.data.role").value("SELLER"))
        .andExpect(jsonPath("$.data.sellerStatus").value("PENDING"));

    // Từ chối seller bằng PATCH.
    support.send(mvc, patch(url), admin, "{\"sellerStatus\":\"REJECTED\"}")
        .andExpect(jsonPath("$.data.sellerStatus").value("REJECTED"));

    // Rời khỏi SELLER thì sellerStatus bị xoá.
    support.send(mvc, patch(url), admin, "{\"role\":\"CUSTOMER\"}")
        .andExpect(jsonPath("$.data.role").value("CUSTOMER"))
        .andExpect(jsonPath("$.data.sellerStatus").doesNotExist());
  }

  @Test
  void invalidUpdatesReturn400Or404() throws Exception {
    User customer = support.user(Role.CUSTOMER, null);
    String url = USERS + "/" + customer.getId();

    // sellerStatus chỉ đặt được cho SELLER.
    support.send(mvc, patch(url), admin, "{\"sellerStatus\":\"APPROVED\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    // Tên toàn khoảng trắng, role không tồn tại.
    support.send(mvc, patch(url), admin, "{\"name\":\"   \"}").andExpect(status().isBadRequest());
    support.send(mvc, patch(url), admin, "{\"role\":\"BOSS\"}").andExpect(status().isBadRequest());
    // Id không tồn tại.
    support.send(mvc, patch(USERS + "/" + UUID.randomUUID()), admin, "{\"name\":\"X\"}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
  }

  // ---------- vô hiệu hoá ----------

  @Test
  void disablingBlocksLoginAndRefreshButKeepsTheRowAndCanBeUndone() throws Exception {
    String email = email();
    String id = idOf(support.send(mvc, post(USERS), admin, create(email, "CUSTOMER", null)));
    String refreshToken = refreshTokenOf(login(email, PASSWORD).andExpect(status().isOk()));

    mvc.perform(delete(USERS + "/" + id).header(HttpHeaders.AUTHORIZATION, support.bearer(admin)))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

    // Dòng còn đó nhưng enabled = false.
    assertThat(users.findById(UUID.fromString(id))).get().extracting(User::isEnabled).isEqualTo(false);
    // Không đăng nhập được, và refresh token đã có trước đó cũng không dùng được nữa.
    login(email, PASSWORD).andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
        .andExpect(status().isUnauthorized());

    // Gọi lại trên tài khoản đã khoá vẫn là 204.
    mvc.perform(delete(USERS + "/" + id).header(HttpHeaders.AUTHORIZATION, support.bearer(admin)))
        .andExpect(status().isNoContent());

    // Bật lại bằng PATCH thì đăng nhập được.
    support.send(mvc, patch(USERS + "/" + id), admin, "{\"enabled\":true}")
        .andExpect(jsonPath("$.data.enabled").value(true));
    login(email, PASSWORD).andExpect(status().isOk());
  }

  @Test
  void disablingAnUnknownUserIs404() throws Exception {
    mvc.perform(delete(USERS + "/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, support.bearer(admin)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
  }

  // ---------- admin không tự khoá mình ----------

  @Test
  void adminCannotLockOrDemoteTheirOwnAccount() throws Exception {
    String self = USERS + "/" + admin.getId();

    support.send(mvc, patch(self), admin, "{\"enabled\":false}").andExpect(status().isBadRequest());
    support.send(mvc, patch(self), admin, "{\"role\":\"CUSTOMER\"}").andExpect(status().isBadRequest());
    mvc.perform(delete(self).header(HttpHeaders.AUTHORIZATION, support.bearer(admin)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

    // Sửa tên của chính mình thì được, và tài khoản vẫn bật.
    support.send(mvc, patch(self), admin, "{\"name\":\"Boss\",\"enabled\":true}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("Boss"));
    assertThat(users.findById(admin.getId())).get().extracting(User::isEnabled).isEqualTo(true);
  }

  // ---------- duyệt seller ----------

  @Test
  void pendingSellerCannotSellUntilAdminApprovesAndCannotAfterSuspend() throws Exception {
    User seller = support.user(Role.SELLER, SellerStatus.PENDING);
    Category category = support.category();
    String product =
        "{\"name\":\"P\",\"description\":\"d\",\"imageUrl\":\"https://example.com/p.jpg\",\"price\":\"10.00\",\"stock\":1,\"categoryId\":\""
            + category.getId()
            + "\"}";

    // Chưa duyệt: 403.
    support.send(mvc, post("/api/v1/seller/products"), seller, product).andExpect(status().isForbidden());

    // Admin duyệt: seller tạo được product (cùng token cũ, vì trạng thái được kiểm tra trong DB).
    approve(seller)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.role").value("SELLER"))
        .andExpect(jsonPath("$.data.sellerStatus").value("APPROVED"));
    support.send(mvc, post("/api/v1/seller/products"), seller, product).andExpect(status().isCreated());
    // Shop công khai hiện ra sau khi được duyệt.
    mvc.perform(get("/api/v1/sellers/" + seller.getId())).andExpect(status().isOk());

    // Tạm khoá: mất quyền ngay.
    suspend(seller).andExpect(status().isOk()).andExpect(jsonPath("$.data.sellerStatus").value("SUSPENDED"));
    support.send(mvc, post("/api/v1/seller/products"), seller, product).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/sellers/" + seller.getId())).andExpect(status().isNotFound());

    // Duyệt lại sau khi bị khoá.
    approve(seller).andExpect(jsonPath("$.data.sellerStatus").value("APPROVED"));
    support.send(mvc, post("/api/v1/seller/products"), seller, product).andExpect(status().isCreated());
  }

  @Test
  void rejectedSellerCanBeApprovedLater() throws Exception {
    User seller = support.user(Role.SELLER, SellerStatus.PENDING);

    support.send(mvc, patch(USERS + "/" + seller.getId()), admin, "{\"sellerStatus\":\"REJECTED\"}")
        .andExpect(jsonPath("$.data.sellerStatus").value("REJECTED"));
    approve(seller).andExpect(jsonPath("$.data.sellerStatus").value("APPROVED"));
  }

  @Test
  void approveAndSuspendRejectInvalidTransitionsAndTargets() throws Exception {
    User approved = support.user(Role.SELLER, SellerStatus.APPROVED);
    User pending = support.user(Role.SELLER, SellerStatus.PENDING);
    User customer = support.user(Role.CUSTOMER, null);

    approve(approved).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    suspend(pending).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    // Không phải seller: 400. Không tồn tại: 404.
    approve(customer).andExpect(status().isBadRequest());
    suspend(customer).andExpect(status().isBadRequest());
    mvc.perform(patch(SELLERS + "/" + UUID.randomUUID() + "/approve").header(HttpHeaders.AUTHORIZATION, support.bearer(admin)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
  }

  @Test
  void approvedSellerAppearsInSellerListAndNotInCustomerList() throws Exception {
    User seller = support.user(Role.SELLER, SellerStatus.PENDING);
    approve(seller).andExpect(status().isOk());

    mvc.perform(get(USERS).param("role", "SELLER").param("search", seller.getEmail()).header(HttpHeaders.AUTHORIZATION, support.bearer(admin)))
        .andExpect(jsonPath("$.data.content[*].sellerStatus", hasItem("APPROVED")));
    mvc.perform(get(USERS).param("role", "CUSTOMER").param("search", seller.getEmail()).header(HttpHeaders.AUTHORIZATION, support.bearer(admin)))
        .andExpect(jsonPath("$.data.content[*].id", not(hasItem(seller.getId().toString()))));
  }

  // ---------- hàm hỗ trợ ----------

  private ResultActions approve(User seller) throws Exception {
    return mvc.perform(
        patch(SELLERS + "/" + seller.getId() + "/approve").header(HttpHeaders.AUTHORIZATION, support.bearer(admin)));
  }

  private ResultActions suspend(User seller) throws Exception {
    return mvc.perform(
        patch(SELLERS + "/" + seller.getId() + "/suspend").header(HttpHeaders.AUTHORIZATION, support.bearer(admin)));
  }

  private ResultActions login(String email, String password) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
  }

  private static String email() {
    return "u-" + UUID.randomUUID() + "@test.com";
  }

  private static String create(String email, String role, Boolean enabled) {
    return "{\"email\":\""
        + email
        + "\",\"password\":\""
        + PASSWORD
        + "\",\"name\":\"Created\",\"role\":\""
        + role
        + "\""
        + (enabled == null ? "" : ",\"enabled\":" + enabled)
        + "}";
  }

  private static String idOf(ResultActions result) throws Exception {
    return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.id");
  }

  private static String refreshTokenOf(ResultActions result) throws Exception {
    return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.refreshToken");
  }
}
