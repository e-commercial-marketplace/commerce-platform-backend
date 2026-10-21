package com.marketplace.dto.request;

import com.marketplace.common.util.VietnamesePhone;
import com.marketplace.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body của {@code POST /admin/users}: admin được tạo user với mọi role.
 *
 * @param password tối đa 72 ký tự vì BCrypt chỉ dùng 72 byte đầu
 * @param enabled {@code null} nghĩa là {@code true}
 * @param phone tuỳ chọn, số di động Việt Nam
 * @param address tuỳ chọn, tối đa 500 ký tự
 */
public record AdminUserCreateRequest(
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(min = 8, max = 72) String password,
    @NotBlank @Size(max = 255) String name,
    @NotNull Role role,
    Boolean enabled,
    @Pattern(regexp = VietnamesePhone.PATTERN, message = "must be a Vietnamese mobile number") String phone,
    @Size(max = 500) String address) {}
