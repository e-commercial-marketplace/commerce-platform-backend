package com.marketplace.service;

import com.marketplace.dto.request.UserProfileUpdateRequest;
import com.marketplace.dto.response.UserProfileResponse;
import java.util.UUID;

/** Nghiệp vụ hồ sơ user. */
public interface UserService {

  /** Hồ sơ của chính user đang đăng nhập. */
  UserProfileResponse getProfile(UUID userId);

  /** Người dùng tự sửa tên, số điện thoại, địa chỉ; trường {@code null} thì giữ nguyên. */
  UserProfileResponse updateProfile(UUID userId, UserProfileUpdateRequest request);
}
