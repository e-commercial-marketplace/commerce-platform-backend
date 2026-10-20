package com.marketplace.dto.request;

import com.marketplace.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body của {@code POST /auth/register}.
 *
 * @param password tối đa 72 ký tự vì BCrypt chỉ dùng 72 byte đầu
 * @param role {@code null} nghĩa là CUSTOMER; ADMIN bị service từ chối
 */
public record RegisterRequest(
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(min = 8, max = 72) String password,
    @NotBlank @Size(max = 255) String name,
    Role role) {}
