package com.marketplace.dto.response;

import com.marketplace.enums.Role;
import java.util.UUID;

/** Thông tin user trả cho chính chủ; không bao giờ chứa password hash. */
public record UserSummaryResponse(UUID id, String email, String name, Role role) {}
