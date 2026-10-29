package com.marketplace.service;

import com.marketplace.entity.User;
import java.util.UUID;

/** Quản lý refresh token opaque: phát hành, xoay vòng (rotation) và thu hồi. */
public interface RefreshTokenService {

  /** Lưu hash và trả token thô cho client; token thô không được lưu ở đâu khác. */
  String createRefreshToken(User user);

  /**
   * Dùng token đúng một lần: revoke và trả user sở hữu để phát hành cặp token mới.
   *
   * @throws com.marketplace.common.exception.BusinessException 401 nếu token không tồn tại, đã bị
   *     revoke, hết hạn hoặc user bị khoá
   */
  User useRefreshToken(String rawToken);

  /** Logout: chỉ revoke token thuộc về {@code currentUserId}. */
  void revokeRefreshToken(String rawToken, UUID currentUserId);

  /** Thu hồi mọi refresh token còn hiệu lực của user, không cần biết token thô. */
  void revokeAllRefreshTokens(UUID userId);
}
