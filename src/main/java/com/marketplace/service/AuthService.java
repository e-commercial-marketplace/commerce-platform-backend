package com.marketplace.service;

import com.marketplace.dto.request.LoginRequest;
import com.marketplace.dto.request.RefreshTokenRequest;
import com.marketplace.dto.request.RegisterRequest;
import com.marketplace.dto.response.AuthResponse;
import java.util.UUID;

/** Luồng xác thực: đăng ký, đăng nhập, xoay vòng refresh token và đăng xuất. */
public interface AuthService {

  AuthResponse register(RegisterRequest request);

  AuthResponse login(LoginRequest request);

  AuthResponse refresh(RefreshTokenRequest request);

  void logout(RefreshTokenRequest request, UUID currentUserId);
}
