package com.marketplace.security;

import com.marketplace.enums.Role;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Principal đã xác thực, dựng từ access token (không truy vấn DB mỗi request).
 *
 * <p>Controller lấy bằng {@code @AuthenticationPrincipal AuthUser me}; service dùng {@code me.id()}
 * cho ownership check thay vì tin userId client gửi lên.
 */
public record AuthUser(UUID id, Role role) {

  /** Authority dạng {@code ROLE_<role>} để {@code hasRole('ADMIN')} hoạt động. */
  public List<GrantedAuthority> authorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
  }
}
