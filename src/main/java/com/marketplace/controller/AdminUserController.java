package com.marketplace.controller;

import com.marketplace.common.response.ApiResponse;
import com.marketplace.common.response.PageResponse;
import com.marketplace.dto.request.AdminUserCreateRequest;
import com.marketplace.dto.request.AdminUserUpdateRequest;
import com.marketplace.dto.response.UserResponse;
import com.marketplace.enums.Role;
import com.marketplace.security.AuthUser;
import com.marketplace.service.AdminUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin quản lý user và duyệt seller. URL thật là {@code /api/v1/admin/...}; role ADMIN được chặn ở
 * {@code SecurityConfig} (luật {@code /api/v1/admin/**}). Danh tính admin lấy từ token để chặn việc
 * admin tự khoá chính mình.
 */
@Validated
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminUserController {

  private final AdminUserService adminUserService;

  @GetMapping("/users")
  public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> list(
      @RequestParam(required = false) Role role,
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return ResponseEntity.ok(ApiResponse.ok(adminUserService.list(role, search, page, size)));
  }

  @GetMapping("/users/{id}")
  public ResponseEntity<ApiResponse<UserResponse>> get(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.ok(adminUserService.get(id)));
  }

  @PostMapping("/users")
  public ResponseEntity<ApiResponse<UserResponse>> create(
      @Valid @RequestBody AdminUserCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(adminUserService.create(request)));
  }

  @PatchMapping("/users/{id}")
  public ResponseEntity<ApiResponse<UserResponse>> update(
      @PathVariable UUID id,
      @Valid @RequestBody AdminUserUpdateRequest request,
      @AuthenticationPrincipal AuthUser me) {
    return ResponseEntity.ok(ApiResponse.ok(adminUserService.update(id, request, me.id())));
  }

  // 204 No Content: tài khoản được vô hiệu hoá (không xoá dòng) và không có body.
  @DeleteMapping("/users/{id}")
  public ResponseEntity<Void> disable(@PathVariable UUID id, @AuthenticationPrincipal AuthUser me) {
    adminUserService.disable(id, me.id());
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/sellers/{id}/approve")
  public ResponseEntity<ApiResponse<UserResponse>> approve(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.ok(adminUserService.approveSeller(id)));
  }

  @PatchMapping("/sellers/{id}/suspend")
  public ResponseEntity<ApiResponse<UserResponse>> suspend(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.ok(adminUserService.suspendSeller(id)));
  }
}
