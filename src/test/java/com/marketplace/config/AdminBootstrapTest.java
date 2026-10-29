package com.marketplace.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marketplace.entity.User;
import com.marketplace.enums.Role;
import com.marketplace.repository.UserRepository;
import com.marketplace.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Kiểm tra {@link AdminBootstrap}: admin đầu tiên chỉ được tạo khi có đủ email + mật khẩu trong biến
 * môi trường và chưa có admin nào.
 *
 * <p>Đây là unit test (không khởi động Spring): {@link UserRepository} được giả bằng Mockito, còn
 * {@link PasswordEncoder} dùng BCrypt thật để kiểm tra mật khẩu được băm đúng.
 */
@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

  // Repository giả: ta quyết định nó "trả lời" gì và kiểm tra xem code có gọi save() hay không.
  @Mock private UserRepository users;

  private final PasswordEncoder encoder = new BCryptPasswordEncoder();

  /** Dựng AdminBootstrap rồi gán email/mật khẩu như thể chúng đến từ biến môi trường. */
  private AdminBootstrap bootstrap(String email, String password) {
    // UserService thật: buildNewUser chỉ dựng đối tượng User, không đọc ghi DB và không cần mapper.
    AdminBootstrap bootstrap = new AdminBootstrap(users, encoder, new UserServiceImpl(users, null));
    // email và password là field @Value (không có setter), nên gán bằng reflection trong test.
    ReflectionTestUtils.setField(bootstrap, "email", email);
    ReflectionTestUtils.setField(bootstrap, "password", password);
    return bootstrap;
  }

  @Test
  void createsAdminWhenNoneExists() {
    when(users.existsByRole(Role.ADMIN)).thenReturn(false); // chưa có admin nào

    bootstrap("  Admin@Example.com ", "strong-password").run(null);

    // Bắt lại đối tượng User đã được truyền vào save() để kiểm tra từng trường.
    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(users).save(saved.capture());
    User admin = saved.getValue();
    assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
    // Email được cắt khoảng trắng và đổi chữ thường, giống cách đăng ký chuẩn hoá email.
    assertThat(admin.getEmail()).isEqualTo("admin@example.com");
    // Mật khẩu phải được băm: không lưu bản thô, nhưng BCrypt vẫn so khớp được với mật khẩu gốc.
    assertThat(admin.getPasswordHash()).isNotEqualTo("strong-password");
    assertThat(encoder.matches("strong-password", admin.getPasswordHash())).isTrue();
  }

  @Test
  void doesNothingWhenAnAdminAlreadyExists() {
    when(users.existsByRole(Role.ADMIN)).thenReturn(true); // đã có admin

    bootstrap("admin@example.com", "strong-password").run(null);

    // Không tạo thêm admin thứ hai dù biến môi trường vẫn được đặt.
    verify(users, never()).save(any());
  }

  @Test
  void doesNothingWhenCredentialsAreMissing() {
    // Thiếu email, hoặc thiếu mật khẩu, hoặc cả hai: bỏ qua hoàn toàn (không tạo admin, không hỏi DB).
    bootstrap("", "strong-password").run(null);
    bootstrap("admin@example.com", "").run(null);
    bootstrap("", "").run(null);

    verify(users, never()).save(any());
  }
}
