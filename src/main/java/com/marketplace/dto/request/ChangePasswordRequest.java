package com.marketplace.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body của {@code PATCH /users/me/password}.
 *
 * @param currentPassword mật khẩu đang dùng, để chứng minh đúng chủ tài khoản
 * @param newPassword 8 đến 72 ký tự và phải khác mật khẩu hiện tại (service kiểm tra)
 */
public record ChangePasswordRequest(
    @NotBlank String currentPassword, @NotBlank @Size(min = 8, max = 72) String newPassword) {}
