package com.marketplace.dto.request;

import com.marketplace.enums.Role;
import com.marketplace.enums.SellerStatus;
import jakarta.validation.constraints.Size;

/**
 * Body của {@code PATCH /admin/users/{id}}: trường nào {@code null} thì giữ nguyên.
 *
 * @param sellerStatus chỉ áp dụng cho SELLER; đây cũng là cách từ chối seller ({@code REJECTED})
 */
public record AdminUserUpdateRequest(
    @Size(max = 255) String name, Role role, SellerStatus sellerStatus, Boolean enabled) {}
