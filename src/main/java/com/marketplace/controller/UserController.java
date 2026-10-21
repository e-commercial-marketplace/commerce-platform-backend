package com.marketplace.controller;

import com.marketplace.common.response.ApiResponse;
import com.marketplace.dto.request.ChangePasswordRequest;
import com.marketplace.dto.request.UserProfileUpdateRequest;
import com.marketplace.dto.response.AuthResponse;
import com.marketplace.dto.response.UserProfileResponse;
import com.marketplace.security.AuthUser;
import com.marketplace.service.AuthService;
import com.marketplace.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint hồ sơ của chính user; danh tính lấy từ token, không nhận userId từ client. */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;
  private final AuthService authService;

  @GetMapping("/me")
  public ResponseEntity<ApiResponse<UserProfileResponse>> me(@AuthenticationPrincipal AuthUser me) {
    return ResponseEntity.ok(ApiResponse.ok(userService.getProfile(me.id())));
  }

  // Tự sửa hồ sơ (tên, số điện thoại, địa chỉ). Email, role, trạng thái không sửa được ở đây.
  @PatchMapping("/me")
  public ResponseEntity<ApiResponse<UserProfileResponse>> updateMe(
      @AuthenticationPrincipal AuthUser me, @Valid @RequestBody UserProfileUpdateRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(userService.updateProfile(me.id(), request)));
  }

  // Đổi mật khẩu của chính mình; trả cặp token mới vì mọi refresh token cũ đã bị thu hồi.
  @PatchMapping("/me/password")
  public ResponseEntity<ApiResponse<AuthResponse>> changePassword(
      @AuthenticationPrincipal AuthUser me, @Valid @RequestBody ChangePasswordRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(authService.changePassword(me.id(), request)));
  }
}
