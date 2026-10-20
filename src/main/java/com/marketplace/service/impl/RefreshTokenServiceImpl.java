package com.marketplace.service.impl;

import com.marketplace.config.JwtProperties;
import com.marketplace.entity.RefreshToken;
import com.marketplace.entity.User;
import com.marketplace.exception.BusinessException;
import com.marketplace.repository.RefreshTokenRepository;
import com.marketplace.service.RefreshTokenService;
import com.marketplace.util.TimeProvider;
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
@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int TOKEN_BYTES = 32;

  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtProperties jwtProperties;
  private final TimeProvider timeProvider;

  @Override
  @Transactional
  public String issue(User user) {
    byte[] bytes = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(bytes);
    String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    Instant expiresAt = timeProvider.now().plusMillis(jwtProperties.refreshExpirationMs());
    refreshTokenRepository.save(RefreshToken.issue(user, sha256(rawToken), expiresAt));
    return rawToken;
  }

  @Override
  @Transactional
  public User consume(String rawToken) {
    String hash = sha256(rawToken);
    RefreshToken token =
        refreshTokenRepository.findByTokenHash(hash).orElseThrow(this::invalidToken);
    Instant now = timeProvider.now();
    // Đọc user trước câu UPDATE bên dưới để lazy proxy được nạp trong cùng persistence context.
    User user = token.getUser();
    if (!token.isUsable(now) || !user.isEnabled()) {
      throw invalidToken();
    }
    // Hai request refresh song song với cùng token: chỉ một bên revoke được (1 dòng), bên kia 401.
    if (refreshTokenRepository.revokeIfActive(hash, now) != 1) {
      throw invalidToken();
    }
    return user;
  }

  @Override
  @Transactional
  public void revoke(String rawToken, UUID currentUserId) {
    String hash = sha256(rawToken);
    RefreshToken token =
        refreshTokenRepository.findByTokenHash(hash).orElseThrow(this::invalidToken);
    // Không phân biệt "token của người khác" với "không tồn tại" để không lộ thông tin.
    if (!token.getUser().getId().equals(currentUserId)
        || refreshTokenRepository.revokeIfActive(hash, timeProvider.now()) != 1) {
      throw invalidToken();
    }
  }

  private BusinessException invalidToken() {
    return new BusinessException(
        "AUTHENTICATION_REQUIRED", "Invalid or expired refresh token", HttpStatus.UNAUTHORIZED);
  }

  private static String sha256(String rawToken) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
