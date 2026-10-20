package com.marketplace.service.impl;

import com.marketplace.common.exception.BusinessException;
import com.marketplace.common.util.TimeProvider;
import com.marketplace.config.JwtProperties;
import com.marketplace.entity.RefreshToken;
import com.marketplace.entity.User;
import com.marketplace.repository.RefreshTokenRepository;
import com.marketplace.service.RefreshTokenService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh token là 32 byte ngẫu nhiên (Base64Url); DB chỉ giữ SHA-256 hex (64 ký tự) nên lộ DB cũng
 * không dùng lại được token.
 */
@Service // Spring tạo và quản lý class nghiệp vụ này
@RequiredArgsConstructor // Lombok tạo constructor nhận 3 field final bên dưới
public class RefreshTokenServiceImpl implements RefreshTokenService {

  // Bộ sinh số ngẫu nhiên an toàn cho mật mã (khác Random thường, không đoán trước được).
  private static final SecureRandom RANDOM = new SecureRandom();
  // Độ dài token: 32 byte ngẫu nhiên, đủ dài để không ai đoán được.
  private static final int TOKEN_BYTES = 32;

  private final RefreshTokenRepository refreshTokenRepository; // đọc/ghi bảng refresh_tokens
  private final JwtProperties jwtProperties; // lấy thời hạn refresh token (mặc định 7 ngày)
  private final TimeProvider timeProvider; // lấy thời gian hiện tại (dễ thay thế khi test)

  // Tạo refresh token mới cho người dùng. Trả token THÔ cho client, DB chỉ lưu bản băm.
  @Override
  @Transactional
  public String issue(User user) {
    // Bước 1: sinh 32 byte ngẫu nhiên.
    byte[] bytes = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(bytes);
    // Bước 2: đổi sang chuỗi chữ để gửi qua HTTP (Base64 an toàn cho URL, bỏ dấu "=" ở cuối).
    String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    // Bước 3: tính hạn dùng = bây giờ + 7 ngày.
    Instant expiresAt = timeProvider.now().plusMillis(jwtProperties.refreshExpirationMs());
    // Bước 4: lưu vào DB chỉ bản băm SHA-256 của token, KHÔNG lưu token thô.
    // Nếu DB bị lộ, kẻ xấu cũng không dùng lại được token.
    refreshTokenRepository.save(RefreshToken.issue(user, sha256(rawToken), expiresAt));
    // Bước 5: trả token thô cho client; đây là lần duy nhất token thô tồn tại ngoài bộ nhớ.
    return rawToken;
  }

  // Dùng refresh token đúng MỘT lần: kiểm tra, thu hồi, rồi trả về chủ sở hữu để cấp token mới.
  @Override
  @Transactional
  public User consume(String rawToken) {
    // Bước 1: băm token client gửi lên rồi tìm trong DB (DB chỉ có bản băm). Không thấy thì 401.
    String hash = sha256(rawToken);
    RefreshToken token =
        refreshTokenRepository.findByTokenHash(hash).orElseThrow(this::invalidToken);
    Instant now = timeProvider.now();
    // Bước 2: lấy người dùng sở hữu token. Đọc user trước câu UPDATE bên dưới để lazy proxy được nạp trong cùng persistence context.
    User user = token.getUser();
    // Bước 3: token phải còn dùng được (chưa bị thu hồi, chưa hết hạn) và tài khoản không bị khoá.
    if (!token.isUsable(now) || !user.isEnabled()) {
      throw invalidToken();
    }
    // Bước 4: thu hồi token. Câu UPDATE trả về số dòng bị đổi: 1 = mình vừa thu hồi thành công,
    // 0 = ai đó thu hồi trước rồi. Hai request refresh song song với cùng token: chỉ một bên revoke được, bên kia 401.
    if (refreshTokenRepository.revokeIfActive(hash, now) != 1) {
      throw invalidToken();
    }
    // Bước 5: trả người dùng để AuthServiceImpl cấp cặp token mới.
    return user;
  }

  // Đăng xuất: thu hồi một refresh token, nhưng chỉ khi nó thuộc về người đang gọi.
  @Override
  @Transactional
  public void revoke(String rawToken, UUID currentUserId) {
    // Bước 1: băm rồi tìm token trong DB; không thấy thì 401.
    String hash = sha256(rawToken);
    RefreshToken token =
        refreshTokenRepository.findByTokenHash(hash).orElseThrow(this::invalidToken);
    // Bước 2: token phải của chính người gọi VÀ phải thu hồi thành công (đang còn hiệu lực).
    // Không phân biệt "token của người khác" với "không tồn tại" để không lộ thông tin.
    if (!token.getUser().getId().equals(currentUserId)
        || refreshTokenRepository.revokeIfActive(hash, timeProvider.now()) != 1) {
      throw invalidToken();
    }
  }

  // Lỗi 401 dùng chung cho mọi trường hợp refresh token không hợp lệ (cùng một thông báo).
  private BusinessException invalidToken() {
    return new BusinessException(
        "AUTHENTICATION_REQUIRED", "Invalid or expired refresh token", HttpStatus.UNAUTHORIZED);
  }

  // Băm SHA-256 một chuỗi và trả về dạng chữ hex dài 64 ký tự (khớp cột token_hash trong DB).
  // Băm là một chiều: có hash không suy ngược ra được token gốc.
  private static String sha256(String rawToken) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest); // đổi các byte thành chữ hex
    } catch (NoSuchAlgorithmException e) {
      // Mọi JVM đều có SHA-256, nên lỗi này gần như không bao giờ xảy ra.
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
