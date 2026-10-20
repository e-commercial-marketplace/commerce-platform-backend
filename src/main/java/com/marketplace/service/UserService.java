package com.marketplace.service;

import com.marketplace.dto.response.UserSummaryResponse;
import java.util.UUID;

/** Nghiệp vụ hồ sơ user. */
public interface UserService {

  /** Hồ sơ của chính user đang đăng nhập. */
  UserSummaryResponse getProfile(UUID userId);
}
