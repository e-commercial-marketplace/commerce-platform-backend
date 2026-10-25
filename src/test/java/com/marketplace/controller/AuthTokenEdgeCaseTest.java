package com.marketplace.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.marketplace.enums.SellerStatus;
import com.marketplace.entity.RefreshToken;
import com.marketplace.entity.User;
import com.marketplace.enums.Role;
import com.marketplace.repository.RefreshTokenRepository;
import com.marketplace.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Các tình huống biên của token mà {@link AuthIntegrationTest} chưa kiểm tra: token hết hạn, token bị
 * làm giả, role lạ, refresh token hết hạn hoặc của tài khoản bị khoá, và đăng nhập bằng seller/admin.
 *
 * <p>Cách làm: test tự dựng token bằng cùng khoá bí mật của app (đọc từ application-test.yml) nên có
 * thể tạo ra đúng loại token "xấu" mong muốn, rồi kiểm tra server từ chối bằng 401.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthTokenEdgeCaseTest {

  private static final String PASSWORD = "password123";

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository users;
  @Autowired private RefreshTokenRepository refreshTokens;
  @Autowired private PasswordEncoder encoder;

  // Khoá thật của app trong môi trường test; dùng để ký các token "xấu" sao cho chỉ sai đúng một chỗ.
  @Value("${app.jwt.secret}")
  private String secret;

  // ---------- access token ----------

  @Test
  void expiredAccessTokenIs401ButSameBuilderWithFutureExpiryWorks() throws Exception {
    String userId = registeredUserId();

    // Đối chứng: cùng cách dựng token nhưng còn hạn thì phải được chấp nhận.
    // Bước này chứng minh hàm dựng token trong test đúng, nên kết quả 401 bên dưới là do HẾT HẠN chứ không phải lỗi khác.
    String valid = jwt(key(secret), userId, "CUSTOMER", Instant.now().plusSeconds(600));
    callMe(valid).andExpect(status().isOk());

    // Token hết hạn từ 60 giây trước: server phải từ chối.
    String expired = jwt(key(secret), userId, "CUSTOMER", Instant.now().minusSeconds(60));
    callMe(expired)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
  }

  @Test
  void tokenSignedWithAnotherKeyIs401() throws Exception {
    String userId = registeredUserId();

    // Nội dung hoàn toàn hợp lệ nhưng ký bằng khoá khác: chữ ký không khớp nên phải bị từ chối.
    String otherKeyToken =
        jwt(key("another-secret-key-that-is-at-least-32-chars!"), userId, "CUSTOMER",
            Instant.now().plusSeconds(600));
    callMe(otherKeyToken).andExpect(status().isUnauthorized());
  }

  @Test
  void tamperedPayloadIsRejectedEvenIfItClaimsAdmin() throws Exception {
    // Kịch bản tấn công: lấy token thật của một CUSTOMER rồi tự sửa phần nội dung thành role ADMIN.
    String[] parts = loginToken().split("\\.");
    String forgedPayload =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                ("{\"sub\":\"" + UUID.randomUUID() + "\",\"role\":\"ADMIN\",\"exp\":9999999999}")
                    .getBytes(StandardCharsets.UTF_8));
    // Giữ nguyên header và chữ ký cũ, chỉ thay phần nội dung (phần giữa).
    String forged = parts[0] + "." + forgedPayload + "." + parts[2];

    // Chữ ký cũ không còn khớp với nội dung mới nên không thể vào khu vực admin.
    mvc.perform(get("/api/v1/admin/probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void tokenWithUnknownRoleIs401() throws Exception {
    String userId = registeredUserId();

    // Ký đúng khoá, còn hạn, nhưng role không có trong enum Role: parseAccessToken phải coi là không hợp lệ.
    String token = jwt(key(secret), userId, "SUPERUSER", Instant.now().plusSeconds(600));
    callMe(token).andExpect(status().isUnauthorized());
  }

  // ---------- refresh token ----------

  @Test
  void expiredRefreshTokenIs401() throws Exception {
    // Tạo thẳng trong DB một refresh token đã hết hạn (hạn là 60 giây trước).
    User user = users.save(User.builder().email(email()).passwordHash(encoder.encode(PASSWORD)).fullName("Old").role(Role.CUSTOMER).sellerStatus(Role.CUSTOMER == Role.SELLER ? SellerStatus.PENDING : null).build());
    String raw = "expired-refresh-" + UUID.randomUUID();
    // DB chỉ lưu bản băm, nên phải băm giống cách RefreshTokenServiceImpl làm.
    refreshTokens.save(RefreshToken.builder().user(user).tokenHash(sha256(raw)).expiresAt(Instant.now().minusSeconds(60)).build());

    refresh(raw)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
  }

  @Test
  void refreshTokenOfDisabledAccountIs401() throws Exception {
    String email = email();
    String refreshToken = refreshTokenOf(register(email, "CUSTOMER"));

    // Khoá tài khoản SAU khi đã nhận token: refresh token vẫn còn hạn nhưng chủ của nó không còn được phép.
    User user = users.findByEmailIgnoreCase(email).orElseThrow();
    user.setEnabled(false);
    users.save(user);

    refresh(refreshToken).andExpect(status().isUnauthorized());
  }

  // ---------- đăng nhập theo từng role ----------

  @Test
  void sellerCanLoginThroughTheLoginEndpoint() throws Exception {
    String email = email();
    register(email, "SELLER").andExpect(status().isCreated());

    login(email)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.user.role").value("SELLER"));
  }

  @Test
  void adminCanLoginAndReachTheAdminArea() throws Exception {
    // ADMIN không đăng ký công khai được, nên tạo trực tiếp trong DB (giống bước bootstrap).
    String email = email();
    users.save(User.builder().email(email).passwordHash(encoder.encode(PASSWORD)).fullName("Admin").role(Role.ADMIN).sellerStatus(Role.ADMIN == Role.SELLER ? SellerStatus.PENDING : null).build());

    // Đăng nhập thật qua /auth/login để lấy token, rồi dùng token đó vào khu vực admin.
    String token =
        JsonPath.read(
            login(email)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.role").value("ADMIN"))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.data.accessToken");

    mvc.perform(get("/api/v1/admin/probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk());
  }

  // ---------- hàm hỗ trợ ----------

  /** Tạo khoá HMAC từ một chuỗi bí mật (cùng cách JwtServiceImpl làm). */
  private static SecretKey key(String secret) {
    return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  /** Dựng một access token với nội dung và hạn tuỳ ý, ký bằng khoá được truyền vào. */
  private static String jwt(SecretKey key, String userId, String role, Instant expiresAt) {
    return Jwts.builder()
        .subject(userId)
        .claim("role", role)
        .issuedAt(new Date())
        .expiration(Date.from(expiresAt))
        .signWith(key)
        .compact();
  }

  /** SHA-256 dạng chữ hex 64 ký tự, giống cách app lưu refresh token. */
  private static String sha256(String raw) throws Exception {
    byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
    return HexFormat.of().formatHex(digest);
  }

  private ResultActions callMe(String token) throws Exception {
    return mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
  }

  private ResultActions register(String email, String role) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"email\":\"%s\",\"password\":\"%s\",\"name\":\"Test\",\"role\":\"%s\"}"
                    .formatted(email, PASSWORD, role)));
  }

  private ResultActions login(String email) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)));
  }

  private ResultActions refresh(String refreshToken) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)));
  }

  /** Đăng ký một CUSTOMER mới và trả id của họ (để dựng token trỏ tới người dùng có thật). */
  private String registeredUserId() throws Exception {
    return JsonPath.read(body(register(email(), "CUSTOMER")), "$.data.user.id");
  }

  /** Đăng ký rồi trả access token thật của người đó. */
  private String loginToken() throws Exception {
    return JsonPath.read(body(register(email(), "CUSTOMER")), "$.data.accessToken");
  }

  private String refreshTokenOf(ResultActions registered) throws Exception {
    return JsonPath.read(body(registered), "$.data.refreshToken");
  }

  private static String body(ResultActions result) throws Exception {
    return result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
  }

  private static String email() {
    return "edge-" + UUID.randomUUID() + "@test.com";
  }
}
