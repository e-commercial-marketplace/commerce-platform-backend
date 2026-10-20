package com.marketplace.controller;

import com.marketplace.common.response.ApiResponse;
import com.marketplace.dto.response.UserSummaryResponse;
import com.marketplace.security.AuthUser;
import com.marketplace.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint hồ sơ của chính user; danh tính lấy từ token, không nhận userId từ client. */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @GetMapping("/me")
  public ResponseEntity<ApiResponse<UserSummaryResponse>> me(@AuthenticationPrincipal AuthUser me) {
    return ResponseEntity.ok(ApiResponse.ok(userService.getProfile(me.id())));
  }
}
