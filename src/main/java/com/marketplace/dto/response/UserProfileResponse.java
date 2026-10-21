package com.marketplace.dto.response;

import com.marketplace.enums.Role;
import java.util.UUID;

/**
 * Hồ sơ của chính user ({@code GET} và {@code PATCH /users/me}); không bao giờ chứa password hash.
 *
 * @param phone số di động dạng {@code 0xxxxxxxxx}, {@code null} khi chưa nhập
 * @param address địa chỉ, {@code null} khi chưa nhập
 */
public record UserProfileResponse(
    UUID id, String email, String name, Role role, String phone, String address) {}
