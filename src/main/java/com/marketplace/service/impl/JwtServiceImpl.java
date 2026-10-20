package com.marketplace.service.impl;

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

  private static final String ROLE_CLAIM = "role";

  private final JwtProperties props;
  private final SecretKey key;

  public JwtServiceImpl(JwtProperties props) {
    this.props = props;
    // Secret < 32 byte làm Keys ném WeakKeyException lúc khởi động -> fail-fast có chủ đích.
    this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public String generateAccessToken(User user) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(user.getId().toString())
        .claim(ROLE_CLAIM, user.getRole().name())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusMillis(props.accessExpirationMs())))
        .signWith(key)
        .compact();
  }

  @Override
  public Optional<AuthUser> parseAccessToken(String token) {
    try {
      Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
      return Optional.of(
          new AuthUser(
              UUID.fromString(claims.getSubject()),
              Role.valueOf(claims.get(ROLE_CLAIM, String.class))));
    } catch (JwtException | IllegalArgumentException | NullPointerException e) {
      return Optional.empty();
    }
  }

  @Override
  public long accessExpiresInSeconds() {
    return props.accessExpirationMs() / 1000;
  }
}
