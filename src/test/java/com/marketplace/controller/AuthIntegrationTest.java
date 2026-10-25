package com.marketplace.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.marketplace.entity.User;
import com.marketplace.enums.Role;
import com.marketplace.enums.SellerStatus;
import com.marketplace.repository.UserRepository;
import com.marketplace.service.JwtService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Điều kiện hoàn thành Phase 1: đăng ký/đăng nhập/refresh/logout và phân quyền CUSTOMER, SELLER,
 * ADMIN được kiểm chứng qua HTTP thật (MockMvc + H2). Mỗi test dùng email ngẫu nhiên nên không phụ
 * thuộc thứ tự chạy.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

  private static final String PASSWORD = "password123";

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JwtService jwtService;

  // ---------- register ----------

  @Test
  void registerCustomerReturnsTokensAndDefaultsToCustomer() throws Exception {
    String email = randomEmail();
    postJson("/api/v1/auth/register", registerBody(email, null))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
        .andExpect(jsonPath("$.data.expiresIn").value(900))
        .andExpect(jsonPath("$.data.user.email").value(email))
        .andExpect(jsonPath("$.data.user.name").value("Test User"))
        .andExpect(jsonPath("$.data.user.role").value("CUSTOMER"))
        .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist());
  }

  @Test
  void registerSellerIsPendingApproval() throws Exception {
    String email = randomEmail();
    postJson("/api/v1/auth/register", registerBody(email, "SELLER"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.user.role").value("SELLER"));

    User seller = userRepository.findByEmailIgnoreCase(email).orElseThrow();
    assertThat(seller.getSellerStatus()).isEqualTo(SellerStatus.PENDING);
    assertThat(seller.getPasswordHash()).isNotEqualTo(PASSWORD);
  }

  @Test
  void registerAdminIsRejected() throws Exception {
    postJson("/api/v1/auth/register", registerBody(randomEmail(), "ADMIN"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }

  @Test
  void registerUnknownRoleIsBadRequestNotServerError() throws Exception {
    postJson("/api/v1/auth/register", registerBody(randomEmail(), "SUPERUSER"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }

  @Test
  void registerDuplicateEmailIgnoringCaseIsRejected() throws Exception {
    String email = randomEmail();
    postJson("/api/v1/auth/register", registerBody(email, null)).andExpect(status().isCreated());

    postJson("/api/v1/auth/register", registerBody(email.toUpperCase(), null))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }

  @Test
  void registerWithShortPasswordReturnsFieldError() throws Exception {
    String body =
        """
        {"email":"%s","password":"short","name":"Test User"}"""
            .formatted(randomEmail());
    postJson("/api/v1/auth/register", body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.fieldErrors.password").exists());
  }

  // ---------- login ----------

  @Test
  void loginWithCorrectCredentialsReturnsTokens() throws Exception {
    String email = randomEmail();
    postJson("/api/v1/auth/register", registerBody(email, null)).andExpect(status().isCreated());

    postJson("/api/v1/auth/login", loginBody(email, PASSWORD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.data.user.role").value("CUSTOMER"));
  }

  @Test
  void wrongPasswordAndUnknownEmailGiveSameError() throws Exception {
    String email = randomEmail();
    postJson("/api/v1/auth/register", registerBody(email, null)).andExpect(status().isCreated());

    String wrongPassword =
        postJson("/api/v1/auth/login", loginBody(email, "wrong-password"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String unknownEmail =
        postJson("/api/v1/auth/login", loginBody(randomEmail(), PASSWORD))
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat((String) JsonPath.read(wrongPassword, "$.error.message"))
        .isEqualTo(JsonPath.read(unknownEmail, "$.error.message"));
  }

  @Test
  void disabledAccountCannotLogin() throws Exception {
    User user = saveUser(Role.CUSTOMER);
    user.setEnabled(false);
    userRepository.save(user);

    postJson("/api/v1/auth/login", loginBody(user.getEmail(), PASSWORD))
        .andExpect(status().isUnauthorized());
  }

  // ---------- refresh / logout ----------

  @Test
  void refreshRotatesTokenAndOldTokenIsRejected() throws Exception {
    String refreshToken = registerAndGet(randomEmail(), "$.data.refreshToken");

    String rotated =
        postJson("/api/v1/auth/refresh", refreshBody(refreshToken))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat((String) JsonPath.read(rotated, "$.data.refreshToken")).isNotEqualTo(refreshToken);

    postJson("/api/v1/auth/refresh", refreshBody(refreshToken))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
  }

  @Test
  void refreshWithUnknownTokenIsRejected() throws Exception {
    postJson("/api/v1/auth/refresh", refreshBody("not-a-real-token"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void logoutRevokesRefreshToken() throws Exception {
    String json = registerAndGetBody(randomEmail());
    String accessToken = JsonPath.read(json, "$.data.accessToken");
    String refreshToken = JsonPath.read(json, "$.data.refreshToken");

    mockMvc
        .perform(
            post("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(refreshToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    postJson("/api/v1/auth/refresh", refreshBody(refreshToken))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void logoutRequiresAccessToken() throws Exception {
    String refreshToken = registerAndGet(randomEmail(), "$.data.refreshToken");
    postJson("/api/v1/auth/logout", refreshBody(refreshToken))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void userCannotLogoutAnotherUsersRefreshToken() throws Exception {
    String victimRefresh = registerAndGet(randomEmail(), "$.data.refreshToken");
    String attackerAccess = registerAndGet(randomEmail(), "$.data.accessToken");

    mockMvc
        .perform(
            post("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, bearer(attackerAccess))
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(victimRefresh)))
        .andExpect(status().isUnauthorized());

    // Token của nạn nhân vẫn dùng được.
    postJson("/api/v1/auth/refresh", refreshBody(victimRefresh)).andExpect(status().isOk());
  }

  // ---------- authentication / authorization ----------

  @Test
  void meReturnsCurrentUserFromToken() throws Exception {
    String email = randomEmail();
    String accessToken = registerAndGet(email, "$.data.accessToken");

    mockMvc
        .perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.email").value(email));
  }

  @Test
  void protectedEndpointWithoutTokenReturnsJson401() throws Exception {
    mockMvc
        .perform(get("/api/v1/users/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
  }

  @Test
  void protectedEndpointWithGarbageTokenReturns401() throws Exception {
    mockMvc
        .perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer("garbage")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void customerAndSellerCannotAccessAdminArea() throws Exception {
    for (Role role : new Role[] {Role.CUSTOMER, Role.SELLER}) {
      mockMvc
          .perform(get("/api/v1/admin/probe").header(HttpHeaders.AUTHORIZATION, tokenFor(role)))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }
  }

  @Test
  void adminCanAccessAdminArea() throws Exception {
    mockMvc
        .perform(get("/api/v1/admin/probe").header(HttpHeaders.AUTHORIZATION, tokenFor(Role.ADMIN)))
        .andExpect(status().isOk());
  }

  @Test
  void methodSecurityAllowsSellerAndDeniesCustomerWith403() throws Exception {
    mockMvc
        .perform(get("/api/v1/seller-probe").header(HttpHeaders.AUTHORIZATION, tokenFor(Role.SELLER)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            get("/api/v1/seller-probe").header(HttpHeaders.AUTHORIZATION, tokenFor(Role.CUSTOMER)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }

  // ---------- helpers ----------

  private ResultActions postJson(String url, String body) throws Exception {
    return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private String registerAndGetBody(String email) throws Exception {
    return postJson("/api/v1/auth/register", registerBody(email, null))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String registerAndGet(String email, String jsonPath) throws Exception {
    return JsonPath.read(registerAndGetBody(email), jsonPath);
  }

  private User saveUser(Role role) {
    return userRepository.save(
        User.builder().email(randomEmail()).passwordHash(passwordEncoder.encode(PASSWORD)).fullName("Probe " + role).role(role).sellerStatus(role == Role.SELLER ? SellerStatus.PENDING : null).build());
  }

  private String tokenFor(Role role) {
    return bearer(jwtService.generateAccessToken(saveUser(role)));
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }

  private static String randomEmail() {
    return "user-" + UUID.randomUUID() + "@test.com";
  }

  private static String registerBody(String email, String role) {
    String roleField = role == null ? "" : ",\"role\":\"" + role + "\"";
    return "{\"email\":\"%s\",\"password\":\"%s\",\"name\":\"Test User\"%s}"
        .formatted(email, PASSWORD, roleField);
  }

  private static String loginBody(String email, String password) {
    return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
  }

  private static String refreshBody(String refreshToken) {
    return "{\"refreshToken\":\"%s\"}".formatted(refreshToken);
  }
}
