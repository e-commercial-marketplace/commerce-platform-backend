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
// "record" là class chỉ để chứa dữ liệu: Java tự tạo constructor và getter id(), role().
// Hai thông tin này đủ để biết "ai" (id) và "được làm gì" (role).
public record AuthUser(UUID id, Role role) {

  /** Authority dạng {@code ROLE_<role>} để {@code hasRole('ADMIN')} hoạt động. */
  public List<GrantedAuthority> authorities() {
    // Quyền trong Spring là danh sách chuỗi; ở đây chỉ có một phần tử, ví dụ "ROLE_CUSTOMER".
    // Tiền tố "ROLE_" là bắt buộc: hasRole("ADMIN") tự thêm "ROLE_" rồi so với chuỗi này.
    return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
  }
}
