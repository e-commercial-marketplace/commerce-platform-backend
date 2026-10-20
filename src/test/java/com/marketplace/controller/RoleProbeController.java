package com.marketplace.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller chỉ tồn tại trong test để chứng minh phân quyền theo role.
 *
 * <p>{@code /admin/probe} bị chặn ở tầng URL matcher ({@code /api/v1/admin/**}); {@code
 * /seller-probe} bị chặn bằng {@code @PreAuthorize} để kiểm tra cả nhánh method security.
 */
@RestController
class RoleProbeController {

  @GetMapping("/admin/probe")
  String admin() {
    return "admin-ok";
  }

  @GetMapping("/seller-probe")
  @PreAuthorize("hasRole('SELLER')")
  String seller() {
    return "seller-ok";
  }
}
