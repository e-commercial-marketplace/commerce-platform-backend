package com.marketplace.config;

import com.marketplace.service.UserService;
import com.marketplace.enums.Role;
import com.marketplace.repository.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tạo admin đầu tiên từ biến môi trường {@code APP_ADMIN_EMAIL}/{@code APP_ADMIN_PASSWORD} khi chưa
 * có admin nào. Public registration không bao giờ tạo ADMIN; thiếu một trong hai biến thì bỏ qua.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final UserService userService;

  @Value("${app.admin.email:}")
  private String email;

  @Value("${app.admin.password:}")
  private String password;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (email.isBlank() || password.isBlank() || userRepository.existsByRole(Role.ADMIN)) {
      return;
    }
    String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
    userRepository.save(
        userService.buildNewUser(
            normalizedEmail, passwordEncoder.encode(password), "Administrator", Role.ADMIN));
    log.info("Bootstrapped initial admin account {}", normalizedEmail);
  }
}
