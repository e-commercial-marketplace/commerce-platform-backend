package com.marketplace.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body của {@code POST /auth/password/forgot}: email của tài khoản cần đặt lại mật khẩu. */
public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 255) String email) {}
