package com.marketplace.dto.response;

import com.marketplace.entity.User;
import com.marketplace.enums.Role;
import java.util.UUID;

/** Thông tin user trả cho chính chủ; không bao giờ chứa password hash. */
public record UserSummaryResponse(UUID id, String email, String name, Role role) {

  public static UserSummaryResponse from(User user) {
    return new UserSummaryResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole());
  }
}
