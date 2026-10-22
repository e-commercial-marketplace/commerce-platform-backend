package com.marketplace.dto.response;

import com.marketplace.enums.Role;
import com.marketplace.enums.SellerStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Thông tin user đầy đủ cho admin; không bao giờ chứa password hash.
 *
 * @param sellerStatus chỉ có giá trị khi {@code role = SELLER}, ngược lại là {@code null}
 */
public record UserResponse(
    UUID id,
    String email,
    String name,
    String phone,
    String address,
    Role role,
    SellerStatus sellerStatus,
    boolean enabled,
    Instant createdAt,
    Instant updatedAt) {}
