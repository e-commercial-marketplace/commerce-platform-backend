package com.marketplace.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body của {@code POST /auth/password/reset}.
 *
 * @param code đúng 6 chữ số, lấy từ email
 * @param newPassword 8 đến 72 ký tự, giống quy tắc khi đăng ký
 */
public record ResetPasswordRequest(
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Pattern(regexp = "\\d{6}") String code,
    @NotBlank @Size(min = 8, max = 72) String newPassword) {}
