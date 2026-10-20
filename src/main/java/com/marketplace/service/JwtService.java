package com.marketplace.service;

import com.marketplace.entity.User;
import com.marketplace.security.AuthUser;
import java.util.Optional;

/** Phát hành và xác minh access token (JWT ký HMAC). */
public interface JwtService {

  String generateAccessToken(User user);

  /** Token sai chữ ký, hết hạn hoặc hỏng trả {@link Optional#empty()}; không ném exception. */
  Optional<AuthUser> parseAccessToken(String token);

  /** Giá trị {@code expiresIn} trả cho client, đơn vị giây. */
  long accessExpiresInSeconds();
}
