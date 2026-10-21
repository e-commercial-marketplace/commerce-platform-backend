package com.marketplace.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.marketplace.repository.UserRepository;
import com.marketplace.service.EmailService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Kiểm thử đổi mật khẩu, quên mật khẩu và đặt lại bằng mã theo features/auth và features/user
 * API_CONTRACT. {@link EmailService} được thay bằng mock: test bắt mã thô ở đó, vì DB chỉ lưu hash.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordApiTest {

  private static final String OLD_PASSWORD = "OldPassw0rd!";
  private static final String NEW_PASSWORD = "NewPassw0rd!";
  private static final String INVALID_CODE = "Invalid or expired reset code";

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;
  // Mock thay EmailServiceImpl: không gửi email thật, và cho test đọc lại mã đã "gửi".
  @MockitoBean private EmailService emailService;

  // ---------- đổi mật khẩu ----------

  @Test
  void changePasswordReturnsNewTokensAndSignsOutOtherDevices() throws Exception {
    String email = email();
    String accessToken = field(register(email), "$.data.accessToken");
    // Một thiết bị khác đang đăng nhập cùng tài khoản.
    String otherDeviceRefresh = field(login(email, OLD_PASSWORD), "$.data.refreshToken");

    String newRefresh =
        field(
            changePassword(accessToken, OLD_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value(email)),
            "$.data.refreshToken");

    // Thiết bị khác bị đăng xuất ở lần refresh tiếp theo; thiết bị hiện tại vẫn refresh được bằng token mới.
    refresh(otherDeviceRefresh).andExpect(status().isUnauthorized());
    refresh(newRefresh).andExpect(status().isOk());
    // Mật khẩu cũ hết dùng được, mật khẩu mới dùng được.
    login(email, OLD_PASSWORD).andExpect(status().isUnauthorized());
    login(email, NEW_PASSWORD).andExpect(status().isOk());
  }

  @Test
  void changePasswordRejectsWrongCurrentSameOrShortPassword() throws Exception {
    String email = email();
    String accessToken = field(register(email), "$.data.accessToken");

    // Sai mật khẩu hiện tại: 400 chứ không phải 401, để app không tưởng phiên đã hết hạn.
    changePassword(accessToken, "WrongPassw0rd!", NEW_PASSWORD)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    // Mật khẩu mới trùng mật khẩu cũ, hoặc quá ngắn.
    changePassword(accessToken, OLD_PASSWORD, OLD_PASSWORD).andExpect(status().isBadRequest());
    changePassword(accessToken, OLD_PASSWORD, "short").andExpect(status().isBadRequest());
    // Mật khẩu không đổi sau các lần thất bại.
    login(email, OLD_PASSWORD).andExpect(status().isOk());
  }

  @Test
  void changePasswordNeedsAnAccessToken() throws Exception {
    mvc.perform(
            patch("/api/v1/users/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(changeBody(OLD_PASSWORD, NEW_PASSWORD)))
        .andExpect(status().isUnauthorized());
  }

  // ---------- quên mật khẩu ----------

  @Test
  void forgotPasswordNeverRevealsWhetherTheEmailExists() throws Exception {
    // Email không tồn tại: vẫn 200, nhưng không gửi gì.
    forgot("nobody-" + UUID.randomUUID() + "@test.com").andExpect(status().isOk());
    verify(emailService, never()).sendPasswordResetCode(anyString(), anyString(), anyLong());

    // Email có thật: cùng response 200, và một email được gửi tới đúng địa chỉ.
    String email = email();
    register(email);
    forgot(email).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
    verify(emailService, times(1)).sendPasswordResetCode(eq(email), anyString(), eq(15L));
  }

  @Test
  void disabledAccountsGetNoCodeAndMalformedEmailIs400() throws Exception {
    String email = email();
    register(email);
    users.findByEmailIgnoreCase(email).ifPresent(u -> {
      u.setEnabled(false);
      users.save(u);
    });

    forgot(email).andExpect(status().isOk());
    verify(emailService, never()).sendPasswordResetCode(anyString(), anyString(), anyLong());

    forgot("not-an-email").andExpect(status().isBadRequest());
  }

  @Test
  void aSecondRequestWithinTheCooldownSendsNothing() throws Exception {
    String email = email();
    register(email);

    forgot(email).andExpect(status().isOk());
    forgot(email).andExpect(status().isOk()); // chưa qua 60 giây: bị bỏ qua im lặng

    verify(emailService, times(1)).sendPasswordResetCode(eq(email), anyString(), anyLong());
  }

  // ---------- đặt lại mật khẩu ----------

  @Test
  void resetWithTheEmailedCodeChangesThePasswordAndSignsOutEverywhere() throws Exception {
    String email = email();
    String oldRefresh = field(register(email), "$.data.refreshToken");
    String code = requestCode(email);

    reset(email, code, NEW_PASSWORD).andExpect(status().isOk());

    login(email, NEW_PASSWORD).andExpect(status().isOk());
    login(email, OLD_PASSWORD).andExpect(status().isUnauthorized());
    refresh(oldRefresh).andExpect(status().isUnauthorized()); // phiên cũ bị thu hồi
    // Mã chỉ dùng được một lần.
    reset(email, code, "Another1Pass!")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value(INVALID_CODE));
  }

  @Test
  void fiveWrongAttemptsLockTheCode() throws Exception {
    String email = email();
    register(email);
    String code = requestCode(email);
    String wrong = code.equals("000000") ? "111111" : "000000";

    for (int i = 0; i < 5; i++) {
      reset(email, wrong, NEW_PASSWORD).andExpect(status().isBadRequest());
    }
    // Đã sai đủ 5 lần: kể cả mã đúng cũng không dùng được nữa.
    reset(email, code, NEW_PASSWORD).andExpect(status().isBadRequest());
    login(email, OLD_PASSWORD).andExpect(status().isOk());
  }

  @Test
  void anExpiredCodeIsRejected() throws Exception {
    String email = email();
    register(email);
    String code = requestCode(email);
    // Giả lập đã quá 15 phút: lùi hạn của mọi mã của user về quá khứ.
    jdbc.update(
        "update password_reset_codes set expires_at = ? where user_id = ?",
        Timestamp.from(Instant.now().minusSeconds(60)),
        userId(email));

    reset(email, code, NEW_PASSWORD)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value(INVALID_CODE));
  }

  @Test
  void aNewCodeReplacesTheOldOne() throws Exception {
    String email = email();
    register(email);
    String first = requestCode(email);
    // Giả lập đã qua thời gian chờ 60 giây để được xin mã mới.
    jdbc.update(
        "update password_reset_codes set created_at = ? where user_id = ?",
        Timestamp.from(Instant.now().minusSeconds(120)),
        userId(email));

    forgot(email).andExpect(status().isOk());
    ArgumentCaptor<String> codes = ArgumentCaptor.forClass(String.class);
    verify(emailService, times(2)).sendPasswordResetCode(eq(email), codes.capture(), anyLong());
    String second = codes.getAllValues().get(1);

    if (!first.equals(second)) { // hai mã ngẫu nhiên hiếm khi trùng; trùng thì phần này không kiểm được
      reset(email, first, NEW_PASSWORD).andExpect(status().isBadRequest());
    }
    reset(email, second, NEW_PASSWORD).andExpect(status().isOk());
  }

  @Test
  void unknownEmailAndMalformedBodyAreRejected() throws Exception {
    // Email không có mã: cùng thông báo với mã sai, không lộ email có tồn tại hay không.
    reset("nobody-" + UUID.randomUUID() + "@test.com", "123456", NEW_PASSWORD)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value(INVALID_CODE));
    // Mã không đủ 6 chữ số, mật khẩu mới quá ngắn.
    reset(email(), "12345", NEW_PASSWORD).andExpect(status().isBadRequest());
    reset(email(), "12345a", NEW_PASSWORD).andExpect(status().isBadRequest());
    reset(email(), "123456", "short").andExpect(status().isBadRequest());
  }

  // ---------- hàm hỗ trợ ----------

  private static String email() {
    return "pw-" + UUID.randomUUID() + "@test.com";
  }

  private UUID userId(String email) {
    return users.findByEmailIgnoreCase(email).orElseThrow().getId();
  }

  /** Xin mã cho email và trả về mã thô mà mock EmailService đã nhận. */
  private String requestCode(String email) throws Exception {
    forgot(email).andExpect(status().isOk());
    ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
    verify(emailService).sendPasswordResetCode(eq(email), code.capture(), anyLong());
    return code.getValue();
  }

  private ResultActions register(String email) throws Exception {
    return postJson(
            "/api/v1/auth/register",
            "{\"email\":\"" + email + "\",\"password\":\"" + OLD_PASSWORD + "\",\"name\":\"Pw\"}")
        .andExpect(status().isCreated());
  }

  private ResultActions login(String email, String password) throws Exception {
    return postJson(
        "/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
  }

  private ResultActions refresh(String refreshToken) throws Exception {
    return postJson("/api/v1/auth/refresh", "{\"refreshToken\":\"" + refreshToken + "\"}");
  }

  private ResultActions forgot(String email) throws Exception {
    return postJson("/api/v1/auth/password/forgot", "{\"email\":\"" + email + "\"}");
  }

  private ResultActions reset(String email, String code, String newPassword) throws Exception {
    return postJson(
        "/api/v1/auth/password/reset",
        "{\"email\":\"" + email + "\",\"code\":\"" + code + "\",\"newPassword\":\"" + newPassword + "\"}");
  }

  private ResultActions changePassword(String accessToken, String current, String next)
      throws Exception {
    return mvc.perform(
        patch("/api/v1/users/me/password")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(changeBody(current, next)));
  }

  private static String changeBody(String current, String next) {
    return "{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}";
  }

  private ResultActions postJson(String url, String json) throws Exception {
    return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
  }

  private static String field(ResultActions result, String path) throws Exception {
    return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
  }
}
