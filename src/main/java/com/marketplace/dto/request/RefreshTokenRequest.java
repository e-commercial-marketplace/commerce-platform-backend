package com.marketplace.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Body của {@code POST /auth/refresh} và {@code POST /auth/logout}. */
public record RefreshTokenRequest(@NotBlank String refreshToken) {}
