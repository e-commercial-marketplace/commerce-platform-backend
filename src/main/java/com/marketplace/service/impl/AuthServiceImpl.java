package com.marketplace.service.impl;

import com.marketplace.dto.request.LoginRequest;
import com.marketplace.dto.request.RefreshTokenRequest;
import com.marketplace.dto.request.RegisterRequest;
import com.marketplace.dto.response.AuthResponse;
import com.marketplace.dto.response.UserSummaryResponse;
import com.marketplace.entity.User;
import com.marketplace.enums.Role;
import com.marketplace.exception.BusinessException;
import com.marketplace.repository.UserRepository;
import com.marketplace.service.AuthService;
import com.marketplace.service.JwtService;
import com.marketplace.service.RefreshTokenService;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public registration chỉ tạo CUSTOMER hoặc SELLER (PENDING); ADMIN chỉ đến từ bootstrap. Email được
 * chuẩn hoá lowercase để unique index trên {@code users.email} có hiệu lực không phân biệt hoa thường.
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final RefreshTokenService refreshTokenService;

  @Override
  @Transactional
  public AuthResponse register(RegisterRequest request) {
    String email = normalizeEmail(request.email());
    Role role = request.role() == null ? Role.CUSTOMER : request.role();
    if (role == Role.ADMIN) {
      throw validationError("Role ADMIN cannot be registered");
    }
    if (userRepository.existsByEmailIgnoreCase(email)) {
      throw emailTaken();
    }
    User user =
        User.register(
            email, passwordEncoder.encode(request.password()), request.name().trim(), role);
    try {
      // Flush ngay để vi phạm unique (hai request đăng ký cùng email song song) bật ra tại đây.
      userRepository.saveAndFlush(user);
    } catch (DataIntegrityViolationException e) {
      throw emailTaken();
    }
    return issueTokens(user);
  }

  @Override
  @Transactional
  public AuthResponse login(LoginRequest request) {
    User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email())).orElse(null);
    // Cùng một lỗi cho sai email, sai mật khẩu và tài khoản bị khoá để không lộ email đã tồn tại.
    if (user == null
        || !passwordEncoder.matches(request.password(), user.getPasswordHash())
        || !user.isEnabled()) {
      throw new BusinessException(
          "AUTHENTICATION_REQUIRED", "Invalid email or password", HttpStatus.UNAUTHORIZED);
    }
    return issueTokens(user);
  }

  @Override
  @Transactional
  public AuthResponse refresh(RefreshTokenRequest request) {
    return issueTokens(refreshTokenService.consume(request.refreshToken()));
  }

  @Override
  @Transactional
  public void logout(RefreshTokenRequest request, UUID currentUserId) {
    refreshTokenService.revoke(request.refreshToken(), currentUserId);
  }

  private AuthResponse issueTokens(User user) {
    return new AuthResponse(
        jwtService.generateAccessToken(user),
        refreshTokenService.issue(user),
        jwtService.accessExpiresInSeconds(),
        UserSummaryResponse.from(user));
  }

  private static String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private static BusinessException emailTaken() {
    return validationError("Email is already registered");
  }

  private static BusinessException validationError(String message) {
    return new BusinessException("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST);
  }
}
