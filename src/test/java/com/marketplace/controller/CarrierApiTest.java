package com.marketplace.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.marketplace.entity.User;
import com.marketplace.enums.Role;
import com.marketplace.enums.SellerStatus;
import com.marketplace.support.TestSupport;
import java.util.UUID;
import org.hamcrest.Matchers;
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

/** Admin quản lý đơn vị vận chuyển; seller chỉ thấy đơn vị đang hoạt động (FR-SHIP-14). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CarrierApiTest {

  private static final String ADMIN = "/api/v1/admin/carriers";

  @Autowired private MockMvc mvc;
  @Autowired private TestSupport support;

  private User admin;
  private User seller;

  @BeforeEach
  void setUp() {
    admin = support.user(Role.ADMIN, null);
    seller = support.user(Role.SELLER, SellerStatus.APPROVED);
  }

  @Test
  void adminCreatesRenamesAndDeactivatesACarrier() throws Exception {
    String name = unique("GHN");
    String id = idOf(create(name).andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.name").value(name))
        .andExpect(jsonPath("$.data.active").value(true)));

    String renamed = unique("Giao Hang Nhanh");
    update(id, "{\"name\":\"  " + renamed + "  \"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value(renamed)); // bỏ khoảng trắng hai đầu
    update(id, "{\"active\":false}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.active").value(false))
        .andExpect(jsonPath("$.data.name").value(renamed)); // trường không gửi thì giữ nguyên

    // Admin vẫn thấy đơn vị đã tắt.
    mvc.perform(get(ADMIN).header(HttpHeaders.AUTHORIZATION, support.bearer(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[*].name", Matchers.hasItem(renamed)));
  }

  @Test
  void namesAreUniqueIgnoringCaseAndMustNotBeBlank() throws Exception {
    String name = unique("GHTK");
    create(name).andExpect(status().isCreated());
    create(name.toLowerCase()).andExpect(status().isBadRequest());
    create("   ").andExpect(status().isBadRequest());

    String other = idOf(create(unique("Viettel Post")));
    update(other, "{\"name\":\"" + name + "\"}").andExpect(status().isBadRequest());
    update(other, "{\"name\":\"\"}").andExpect(status().isBadRequest());
    update(UUID.randomUUID().toString(), "{\"active\":false}").andExpect(status().isNotFound());
  }

  @Test
  void sellersSeeOnlyActiveCarriers() throws Exception {
    String active = unique("Active");
    String inactive = unique("Inactive");
    create(active);
    update(idOf(create(inactive)), "{\"active\":false}");

    mvc.perform(get("/api/v1/seller/carriers").header(HttpHeaders.AUTHORIZATION, support.bearer(seller)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[*].name", Matchers.hasItem(active)))
        .andExpect(jsonPath("$.data[*].name", Matchers.not(Matchers.hasItem(inactive))));
  }

  @Test
  void onlyAdminManagesCarriers() throws Exception {
    User customer = support.user(Role.CUSTOMER, null);
    for (User who : new User[] {customer, seller}) {
      mvc.perform(get(ADMIN).header(HttpHeaders.AUTHORIZATION, support.bearer(who)))
          .andExpect(status().isForbidden());
      support.send(mvc, post(ADMIN), who, "{\"name\":\"X\"}").andExpect(status().isForbidden());
    }
    mvc.perform(get(ADMIN)).andExpect(status().isUnauthorized());
    // Seller chưa được duyệt không xem được danh sách đơn vị.
    User pending = support.user(Role.SELLER, SellerStatus.PENDING);
    mvc.perform(get("/api/v1/seller/carriers").header(HttpHeaders.AUTHORIZATION, support.bearer(pending)))
        .andExpect(status().isForbidden());
  }

  private ResultActions create(String name) throws Exception {
    return mvc.perform(
        post(ADMIN)
            .header(HttpHeaders.AUTHORIZATION, support.bearer(admin))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"" + name + "\"}"));
  }

  private ResultActions update(String id, String json) throws Exception {
    return support.send(mvc, patch(ADMIN + "/" + id), admin, json);
  }

  private static String idOf(ResultActions result) throws Exception {
    return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.id");
  }

  /** DB test dùng chung giữa các test nên tên phải khác nhau. */
  private static String unique(String base) {
    return base + " " + UUID.randomUUID().toString().substring(0, 8);
  }
}
