package com.marketplace.service.impl;

import com.marketplace.common.util.TimeProvider;
import com.marketplace.config.JwtProperties;
import com.marketplace.entity.User;
import com.marketplace.enums.Role;
import com.marketplace.security.AuthUser;
import com.marketplace.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/** Access token chứa {@code sub = userId} và claim {@code role}; không chứa dữ liệu nhạy cảm. */
@Service
public class JwtServiceImpl implements JwtService {

  // Tên của trường "role" bên trong token. Dùng hằng số để lúc tạo và lúc đọc không gõ sai chính tả.
  private static final String ROLE_CLAIM = "role";

  // Cấu hình JWT đọc từ application.yml (secret, thời hạn token).
  private final JwtProperties props;
  // Khoá bí mật dùng để KÝ token lúc tạo và KIỂM TRA chữ ký lúc đọc.
  private final SecretKey key;

  // Nguồn thời gian dùng chung của dự án. Lấy giờ qua TimeProvider (thay vì Instant.now() trực tiếp) cho
  // nhất quán với RefreshTokenServiceImpl và để sau này test có thể điều khiển đồng hồ.
  private final TimeProvider timeProvider;

  public JwtServiceImpl(JwtProperties props, TimeProvider timeProvider) {
    this.props = props;
    this.timeProvider = timeProvider;
    // Đổi chuỗi secret thành khoá HMAC. Secret ngắn hơn 32 byte thì thư viện ném lỗi ngay lúc khởi động
    // (fail-fast có chủ đích: thà app không chạy còn hơn chạy với khoá yếu).
    this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
  }

  // Tạo access token cho một người dùng (gọi lúc đăng nhập/đăng ký/refresh).
  @Override
  public String generateAccessToken(User user) {
    Instant now = timeProvider.now(); // thời điểm hiện tại, dùng cho "tạo lúc" và "hết hạn lúc"
    return Jwts.builder()
        // "sub" (subject): token này của ai, ở đây là id người dùng
        .subject(user.getId().toString())
        // thêm trường "role" để về sau biết quyền mà không phải tra DB
        .claim(ROLE_CLAIM, user.getRole().name())
        // "iat": thời điểm tạo token
        .issuedAt(Date.from(now))
        // "exp": thời điểm hết hạn = bây giờ + thời hạn cấu hình (mặc định 15 phút)
        .expiration(Date.from(now.plusMillis(props.accessExpirationMs())))
        // ký bằng khoá bí mật: ai sửa nội dung token thì chữ ký không còn khớp
        .signWith(key)
        // ghép header.payload.chữ_ký thành một chuỗi duy nhất để trả cho client
        .compact();
  }

  // Đọc và kiểm tra một token. Trả về người dùng nếu hợp lệ, rỗng nếu sai (không ném lỗi ra ngoài).
  @Override
  public Optional<AuthUser> parseAccessToken(String token) {
    try {
      // Bước 1: kiểm tra chữ ký bằng khoá, đồng thời kiểm tra token chưa hết hạn; lấy phần nội dung (claims).
      // Sai chữ ký, hết hạn hoặc token hỏng đều ném JwtException tại dòng này.
      Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
      // Bước 2: dựng AuthUser từ nội dung token.
      return Optional.of(
          new AuthUser(
              UUID.fromString(claims.getSubject()), // "sub" là chuỗi, đổi lại thành UUID
              Role.valueOf(claims.get(ROLE_CLAIM, String.class)))); // đổi chữ "ADMIN"... thành enum Role
    } catch (JwtException | IllegalArgumentException | NullPointerException e) {
      // JwtException: chữ ký sai/hết hạn/token hỏng. IllegalArgumentException: sub hoặc role không đúng định dạng.
      // NullPointerException: token thiếu trường role. Tất cả đều coi là "không hợp lệ".
      return Optional.empty();
    }
  }

  // Số giây access token sống, trả cho client trong trường expiresIn (ví dụ 900 giây).
  @Override
  public long accessTokenLifetimeSeconds() {
    return props.accessExpirationMs() / 1000; // đổi mili giây sang giây
  }
}
