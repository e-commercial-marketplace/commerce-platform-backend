package com.marketplace.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.marketplace.entity.Category;
import com.marketplace.entity.Product;
import com.marketplace.entity.User;
import com.marketplace.enums.Role;
import com.marketplace.enums.SellerStatus;
import com.marketplace.repository.CategoryRepository;
import com.marketplace.repository.ProductRepository;
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
 * Kiểm thử API quản lý category của admin theo contract catalog: tạo, đổi tên, xoá; tên không trùng;
 * không xoá được category còn product; chỉ ADMIN được gọi.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminCategoryApiTest {

  private static final String BASE = "/api/v1/admin/categories";

  @Autowired private MockMvc mvc;
  @Autowired private CategoryRepository categories;
  @Autowired private ProductRepository products;
  @Autowired private TestSupport support;

  private User admin;

  @BeforeEach
  void setUp() {
    admin = support.user(Role.ADMIN, null);
  }

  // ---------- tạo ----------

  @Test
  void adminCreatesACategoryAndItIsListedPublicly() throws Exception {
    String name = "New-" + UUID.randomUUID();

    // Tên có khoảng trắng hai đầu được cắt đi trước khi lưu.
    String id =
        idOf(
            support.send(mvc, post(BASE), admin, "{\"name\":\"  " + name + "  \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value(name)));

    // Khách (không cần token) thấy category mới trong danh sách công khai.
    mvc.perform(get("/api/v1/categories"))
        .andExpect(jsonPath("$.data[*].id", hasItem(id)))
        .andExpect(jsonPath("$.data[*].name", hasItem(name)));
  }

  @Test
  void onlyAdminCanManageCategories() throws Exception {
    String json = "{\"name\":\"Nope-" + UUID.randomUUID() + "\"}";

    // Không có token: 401.
    mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isUnauthorized());

    // Customer và seller đều sai role: 403 FORBIDDEN.
    for (Role role : new Role[] {Role.CUSTOMER, Role.SELLER}) {
      support.send(mvc, post(BASE), support.user(role, null), json)
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }
  }

  @Test
  void blankMissingOrDuplicateNamesReturn400() throws Exception {
    String name = "Dup-" + UUID.randomUUID();
    support.send(mvc, post(BASE), admin, "{\"name\":\"" + name + "\"}").andExpect(status().isCreated());

    String[] invalidBodies = {
      "{\"name\":\"\"}", // trống
      "{\"name\":\"   \"}", // toàn khoảng trắng
      "{}", // thiếu name
      "{\"name\":\"" + "x".repeat(121) + "\"}", // quá 120 ký tự
      "{\"name\":\"" + name + "\"}", // trùng y hệt
      "{\"name\":\"" + name.toUpperCase() + "\"}" // trùng khác hoa thường
    };
    for (String invalid : invalidBodies) {
      support.send(mvc, post(BASE), admin, invalid)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
  }

  // ---------- đổi tên ----------

  @Test
  void renameWorksAndRejectsNamesOfOtherCategories() throws Exception {
    Category mine = category("Mine-" + UUID.randomUUID());
    Category other = category("Other-" + UUID.randomUUID());
    String id = mine.getId().toString();

    // Đổi sang tên mới hợp lệ.
    String renamed = "Renamed-" + UUID.randomUUID();
    support.send(mvc, patch(BASE + "/" + id), admin, "{\"name\":\"" + renamed + "\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value(renamed));

    // Đổi sang tên đang thuộc category khác thì bị từ chối.
    support.send(mvc, patch(BASE + "/" + id), admin, "{\"name\":\"" + other.getName() + "\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

    // Đổi hoa thường của chính tên mình thì được phép (không tính là trùng với category khác).
    support.send(mvc, patch(BASE + "/" + id), admin, "{\"name\":\"" + renamed.toUpperCase() + "\"}")
        .andExpect(status().isOk());

    // Tên trống thì 400; id không tồn tại thì 404.
    support.send(mvc, patch(BASE + "/" + id), admin, "{\"name\":\"\"}").andExpect(status().isBadRequest());
    support.send(mvc, patch(BASE + "/" + UUID.randomUUID()), admin, "{\"name\":\"X\"}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
  }

  // ---------- xoá ----------

  @Test
  void deletingAnEmptyCategoryReturns204() throws Exception {
    Category empty = category("Empty-" + UUID.randomUUID());

    mvc.perform(delete(BASE + "/" + empty.getId()).header(HttpHeaders.AUTHORIZATION, support.bearer(admin)))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

    assertThat(categories.findById(empty.getId())).isEmpty(); // đã bị xoá thật
  }

  @Test
  void categoryWithProductsCannotBeDeleted() throws Exception {
    Category used = category("Used-" + UUID.randomUUID());
    User seller = support.user(Role.SELLER, SellerStatus.APPROVED);
    Product hidden = Product.create("P", "d", null, new BigDecimal("10.00"), 1, used, seller);
    hidden.setActive(false); // product đã ẩn vẫn tính là "còn product", vì còn tham chiếu tới category
    products.save(hidden);

    mvc.perform(delete(BASE + "/" + used.getId()).header(HttpHeaders.AUTHORIZATION, support.bearer(admin)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

    assertThat(categories.findById(used.getId())).isPresent(); // category còn nguyên
  }

  @Test
  void deletingAnUnknownCategoryIs404() throws Exception {
    mvc.perform(delete(BASE + "/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, support.bearer(admin)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
  }

  // ---------- hàm hỗ trợ ----------

  private Category category(String name) {
    return categories.save(Category.create(name));
  }

  private static String idOf(ResultActions result) throws Exception {
    return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.id");
  }
}
