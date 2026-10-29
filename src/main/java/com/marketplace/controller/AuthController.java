package com.marketplace.controller;

import com.marketplace.common.response.ApiResponse;
import com.marketplace.dto.request.ForgotPasswordRequest;
import com.marketplace.dto.request.LoginRequest;
import com.marketplace.dto.request.RefreshTokenRequest;
import com.marketplace.dto.request.RegisterRequest;
import com.marketplace.dto.request.ResetPasswordRequest;
import com.marketplace.dto.response.AuthResponse;
import com.marketplace.security.AuthUser;
import com.marketplace.service.AuthService;
import com.marketplace.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Auth endpoints; URL thật là {@code /api/v1/auth/...} (prefix do {@code WebConfig} thêm). */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final PasswordResetService passwordResetService;

  @PostMapping("/register")
  public ResponseEntity<ApiResponse<AuthResponse>> register(
      @Valid @RequestBody RegisterRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(authService.register(request)));
  }

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<AuthResponse>> login(
      @Valid @RequestBody LoginRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(authService.login(request)));
  }

  @PostMapping("/refresh")
  public ResponseEntity<ApiResponse<AuthResponse>> refresh(
      @Valid @RequestBody RefreshTokenRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(authService.refresh(request)));
  }

  @PostMapping("/logout")
  public ResponseEntity<ApiResponse<Void>> logout(
      @Valid @RequestBody RefreshTokenRequest request, @AuthenticationPrincipal AuthUser me) {
    authService.logout(request, me.id());
    return ResponseEntity.ok(ApiResponse.ok(null));
  }

  // Luôn trả 200 rỗng, kể cả khi email không tồn tại, để không lộ email nào đã đăng ký.
  @PostMapping("/password/forgot")
  public ResponseEntity<ApiResponse<Void>> forgotPassword(
      @Valid @RequestBody ForgotPasswordRequest request) {
    passwordResetService.sendResetCode(request);
    return ResponseEntity.ok(ApiResponse.ok(null));
  }

  // Đặt mật khẩu mới bằng mã trong email; xong thì client đăng nhập lại bằng mật khẩu mới.
  @PostMapping("/password/reset")
  public ResponseEntity<ApiResponse<Void>> resetPassword(
      @Valid @RequestBody ResetPasswordRequest request) {
    passwordResetService.resetPassword(request);
    return ResponseEntity.ok(ApiResponse.ok(null));
  }
}
