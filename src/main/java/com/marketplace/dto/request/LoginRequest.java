package com.marketplace.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Body của {@code POST /auth/login}. */
public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
