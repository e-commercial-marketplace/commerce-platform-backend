package com.marketplace.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketplace.entity.User;
import com.marketplace.enums.Role;
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
 * Kiểm thử cập nhật hồ sơ theo features/user/API_CONTRACT.md (contact rule): người dùng tự sửa tên,
 * số điện thoại Việt Nam, địa chỉ; admin sửa được liên hệ của mọi người dùng.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileApiTest {

  private static final String ME = "/api/v1/users/me";

  @Autowired private MockMvc mvc;
  @Autowired private TestSupport support;

  private User customer;

  @BeforeEach
  void setUp() {
    customer = support.user(Role.CUSTOMER, null, "Old Name");
  }

  // ---------- người dùng tự sửa ----------

  @Test
  void aNewAccountHasNoPhoneOrAddress() throws Exception {
    me(customer)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("Old Name"))
        .andExpect(jsonPath("$.data.phone").doesNotExist())
        .andExpect(jsonPath("$.data.address").doesNotExist())
        .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
  }

  @Test
  void aUserUpdatesTheirOwnProfileAndThePhoneIsNormalized() throws Exception {
    support.send(mvc, patch(ME), customer,
            "{\"name\":\"  Nguyen Van A  \",\"phone\":\"+84912345678\",\"address\":\"  123 Nguyen Trai  \"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("Nguyen Van A"))
        .andExpect(jsonPath("$.data.phone").value("0912345678")) // +84 lưu thành 0
        .andExpect(jsonPath("$.data.address").value("123 Nguyen Trai"));

    // GET trả đúng dữ liệu đã lưu.
    me(customer).andExpect(jsonPath("$.data.phone").value("0912345678"));
  }

  @Test
  void omittedFieldsStayAndEmptyStringsClear() throws Exception {
    support.send(mvc, patch(ME), customer, "{\"phone\":\"0987654321\",\"address\":\"Hanoi\"}")
        .andExpect(status().isOk());

    // Chỉ gửi address: tên và số điện thoại giữ nguyên.
    support.send(mvc, patch(ME), customer, "{\"address\":\"Da Nang\"}")
        .andExpect(jsonPath("$.data.name").value("Old Name"))
        .andExpect(jsonPath("$.data.phone").value("0987654321"))
        .andExpect(jsonPath("$.data.address").value("Da Nang"));

    // Chuỗi rỗng thì xoá.
    support.send(mvc, patch(ME), customer, "{\"phone\":\"\",\"address\":\"\"}")
        .andExpect(jsonPath("$.data.phone").doesNotExist())
        .andExpect(jsonPath("$.data.address").doesNotExist());
  }

  @Test
  void onlyVietnameseMobileNumbersAreAccepted() throws Exception {
    // Đúng: đầu số 03, 05, 07, 08, 09 với 0 hoặc +84.
    for (String ok : new String[] {"0312345678", "0512345678", "0712345678", "0812345678", "+84987654321"}) {
      support.send(mvc, patch(ME), customer, "{\"phone\":\"" + ok + "\"}").andExpect(status().isOk());
    }
    String[] invalid = {
      "0123456789", // đầu số 01 không phải di động hiện hành
      "0212345678", // số bàn
      "091234567", // thiếu một chữ số
      "09123456789", // thừa một chữ số
      "84912345678", // thiếu dấu + trước 84
      "+1 4155552671", // số nước ngoài
      "0912 345 678", // có khoảng trắng
      "abcdefghij"
    };
    for (String bad : invalid) {
      support.send(mvc, patch(ME), customer, "{\"phone\":\"" + bad + "\"}")
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
  }

  @Test
  void blankNameAndLongAddressAreRejected() throws Exception {
    support.send(mvc, patch(ME), customer, "{\"name\":\"   \"}").andExpect(status().isBadRequest());
    support.send(mvc, patch(ME), customer, "{\"name\":\"" + "x".repeat(256) + "\"}")
        .andExpect(status().isBadRequest());
    support.send(mvc, patch(ME), customer, "{\"address\":\"" + "x".repeat(501) + "\"}")
        .andExpect(status().isBadRequest());
    me(customer).andExpect(jsonPath("$.data.name").value("Old Name")); // không đổi gì
  }

  @Test
  void emailAndRoleCannotBeChangedThroughTheProfile() throws Exception {
    support.send(mvc, patch(ME), customer, "{\"email\":\"hacker@test.com\",\"role\":\"ADMIN\",\"name\":\"N\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.email").value(customer.getEmail()))
        .andExpect(jsonPath("$.data.role").value("CUSTOMER"));
  }

  @Test
  void theProfileNeedsAnAccessToken() throws Exception {
    mvc.perform(patch(ME).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
        .andExpect(status().isUnauthorized());
  }

  // ---------- admin toàn quyền ----------

  @Test
  void anAdminCanSetAndClearAnyUsersContact() throws Exception {
    User admin = support.user(Role.ADMIN, null);
    String url = "/api/v1/admin/users/" + customer.getId();

    support.send(mvc, patch(url), admin, "{\"phone\":\"+84912345678\",\"address\":\"45 Le Loi\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.phone").value("0912345678"))
        .andExpect(jsonPath("$.data.address").value("45 Le Loi"));
    // Chủ tài khoản thấy dữ liệu admin vừa sửa.
    me(customer).andExpect(jsonPath("$.data.address").value("45 Le Loi"));

    support.send(mvc, patch(url), admin, "{\"phone\":\"0123\"}").andExpect(status().isBadRequest());
    support.send(mvc, patch(url), admin, "{\"address\":\"\"}")
        .andExpect(jsonPath("$.data.address").doesNotExist())
        .andExpect(jsonPath("$.data.phone").value("0912345678"));
  }

  @Test
  void anAdminCanCreateAUserWithContact() throws Exception {
    User admin = support.user(Role.ADMIN, null);
    String body =
        "{\"email\":\"c-" + UUID.randomUUID() + "@test.com\",\"password\":\"Passw0rd!\",\"name\":\"C\","
            + "\"role\":\"CUSTOMER\",\"phone\":\"0987654321\",\"address\":\"Hue\"}";

    support.send(mvc, post("/api/v1/admin/users"), admin, body)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.phone").value("0987654321"))
        .andExpect(jsonPath("$.data.address").value("Hue"));
  }

  // ---------- hàm hỗ trợ ----------

  private ResultActions me(User who) throws Exception {
    return mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, support.bearer(who)));
  }
}
