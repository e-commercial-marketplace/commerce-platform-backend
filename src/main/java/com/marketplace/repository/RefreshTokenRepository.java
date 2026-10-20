package com.marketplace.repository;

import com.marketplace.entity.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Lưu metadata refresh token; mọi tra cứu đi qua SHA-256 hash, không bao giờ qua token thô. */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  /**
   * Revoke có điều kiện để chống race: hai request dùng cùng một refresh token song song thì chỉ
   * một request nhận được kết quả 1, request còn lại nhận 0 và phải bị từ chối.
   */
  @Modifying
  @Query(
      "update RefreshToken t set t.revokedAt = :now"
          + " where t.tokenHash = :hash and t.revokedAt is null")
  int revokeIfActive(@Param("hash") String hash, @Param("now") Instant now);

  /** Thu hồi mọi refresh token còn hiệu lực của một user (khi admin vô hiệu hoá tài khoản). */
  @Modifying
  @Query(
      "update RefreshToken t set t.revokedAt = :now"
          + " where t.user.id = :userId and t.revokedAt is null")
  int revokeAllActive(@Param("userId") UUID userId, @Param("now") Instant now);
}
