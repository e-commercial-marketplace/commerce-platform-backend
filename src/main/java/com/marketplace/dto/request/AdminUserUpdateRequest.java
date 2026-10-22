package com.marketplace.dto.request;

import com.marketplace.common.validation.NullOrNotBlank;
import com.marketplace.common.validation.VietnamesePhoneNumber;
import com.marketplace.enums.Role;
import com.marketplace.enums.SellerStatus;
import jakarta.validation.constraints.Size;

/**
 * Body của {@code PATCH /admin/users/{id}}: trường nào {@code null} thì giữ nguyên.
 *
 * @param sellerStatus chỉ áp dụng cho SELLER; đây cũng là cách từ chối seller ({@code REJECTED})
 * @param phone số di động Việt Nam, hoặc chuỗi rỗng để xoá
 * @param address tối đa 500 ký tự, hoặc chuỗi rỗng để xoá
 */
public record AdminUserUpdateRequest(
    @NullOrNotBlank @Size(max = 255) String name,
    Role role,
    SellerStatus sellerStatus,
    Boolean enabled,
    @VietnamesePhoneNumber String phone,
    @Size(max = 500) String address) {}
