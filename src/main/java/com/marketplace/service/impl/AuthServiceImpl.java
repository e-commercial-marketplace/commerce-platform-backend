package com.marketplace.service.impl;

import com.marketplace.common.exception.BusinessException;
import com.marketplace.common.util.Emails;
import com.marketplace.dto.request.ChangePasswordRequest;
import com.marketplace.dto.request.LoginRequest;
import com.marketplace.dto.request.RefreshTokenRequest;
import com.marketplace.dto.request.RegisterRequest;
import com.marketplace.dto.response.AuthResponse;
import com.marketplace.entity.User;
import com.marketplace.enums.Role;
import com.marketplace.mapper.UserMapper;
import com.marketplace.repository.UserRepository;
import com.marketplace.service.AuthService;
import com.marketplace.service.JwtService;
import com.marketplace.service.RefreshTokenService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public registration chỉ tạo CUSTOMER hoặc SELLER (PENDING); ADMIN chỉ đến từ bootstrap. Email được
 * chuẩn hoá lowercase để unique index trên {@code users.email} có hiệu lực không phân biệt hoa thường.
 */
@Service // đánh dấu đây là class nghiệp vụ để Spring tạo và quản lý
@RequiredArgsConstructor // Lombok tạo constructor nhận 4 field final bên dưới
public class AuthServiceImpl implements AuthService {

  private final UserRepository userRepository; // đọc/ghi người dùng trong DB
  private final PasswordEncoder passwordEncoder; // băm mật khẩu và so khớp mật khẩu
  private final JwtService jwtService; // tạo access token
  private final RefreshTokenService refreshTokenService; // tạo/thu hồi refresh token
  private final UserMapper userMapper; // đổi User sang thông tin trả cho client (không có mật khẩu)

  // @Transactional: các thao tác DB trong hàm là một khối, có lỗi thì hoàn tác hết, không ghi nửa chừng.
  @Override
  @Transactional
  public AuthResponse register(RegisterRequest request) {
    // Bước 1: chuẩn hoá email (bỏ khoảng trắng, chữ thường) để "A@x.com" và "a@x.com" là một.
    String email = Emails.normalize(request.email());
    // Bước 2: không gửi role thì mặc định là CUSTOMER.
    Role role = request.role() == null ? Role.CUSTOMER : request.role();
    // Bước 3: tuyệt đối không cho tự đăng ký ADMIN từ bên ngoài.
    if (role == Role.ADMIN) {
      throw BusinessException.validation("Role ADMIN cannot be registered");
    }
    // Bước 4: email đã có người dùng thì từ chối.
    if (userRepository.existsByEmailIgnoreCase(email)) {
      throw emailTaken();
    }
    // Bước 5: tạo người dùng mới. Mật khẩu được băm TRƯỚC khi lưu, không bao giờ lưu mật khẩu thô.
    User user =
        User.register(
            email, passwordEncoder.encode(request.password()), request.name().trim(), role);
    try {
      // Bước 6: lưu vào DB. Flush ngay để vi phạm unique (hai request đăng ký cùng email song song) bật ra tại đây.
      userRepository.saveAndFlush(user);
    } catch (DataIntegrityViolationException e) {
      throw emailTaken(); // thua cuộc đua: người kia vừa đăng ký cùng email trước
    }
    // Bước 7: đăng ký xong là đăng nhập luôn, trả về cặp token.
    return issueTokens(user);
  }

  @Override
  @Transactional
  public AuthResponse login(LoginRequest request) {
    // Bước 1: tìm người dùng theo email đã chuẩn hoá; không thấy thì user = null.
    User user = userRepository.findByEmailIgnoreCase(Emails.normalize(request.email())).orElse(null);
    // Bước 2: kiểm tra ba điều kiện cùng lúc. Chỉ cần một điều kiện đúng là báo lỗi:
    //   - không có người dùng với email đó
    //   - mật khẩu nhập vào không khớp hash đã lưu (matches tự băm mật khẩu nhập rồi so)
    //   - tài khoản đã bị khoá
    // Cùng một lỗi cho sai email, sai mật khẩu và tài khoản bị khoá để không lộ email đã tồn tại.
    if (user == null
        || !passwordEncoder.matches(request.password(), user.getPasswordHash())
        || !user.isEnabled()) {
      throw BusinessException.unauthorized("Invalid email or password");
    }
    // Bước 3: mọi thứ đều đúng, phát hành cặp token.
    return issueTokens(user);
  }

  // Đổi refresh token lấy cặp token mới. consume() kiểm tra token, thu hồi nó và trả về chủ sở hữu.
  @Override
  @Transactional
  public AuthResponse refresh(RefreshTokenRequest request) {
    return issueTokens(refreshTokenService.consume(request.refreshToken()));
  }

  // Đăng xuất = thu hồi refresh token. currentUserId lấy từ access token để chỉ thu hồi token của chính mình.
  @Override
  @Transactional
  public void logout(RefreshTokenRequest request, UUID currentUserId) {
    refreshTokenService.revoke(request.refreshToken(), currentUserId);
  }

  // Đổi mật khẩu khi đã đăng nhập. currentUserId lấy từ access token, không nhận từ client.
  @Override
  @Transactional
  public AuthResponse changePassword(UUID currentUserId, ChangePasswordRequest request) {
    // Bước 1: lấy user từ DB (token chỉ mang id và role, không mang mật khẩu).
    User user = userRepository.getRequired(currentUserId);
    // Bước 2: tài khoản bị khoá thì không cấp token mới, dù access token cũ còn hạn.
    if (!user.isEnabled()) {
      throw BusinessException.unauthorized("Account is disabled");
    }
    // Bước 3: mật khẩu hiện tại phải đúng. Trả 400 chứ không phải 401, để app không tưởng phiên đã hết hạn.
    if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
      throw BusinessException.validation("Current password is incorrect");
    }
    // Bước 4: mật khẩu mới phải khác mật khẩu đang dùng.
    if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
      throw BusinessException.validation("New password must be different from the current password");
    }
    // Bước 5: lưu hash của mật khẩu mới, rồi thu hồi mọi refresh token cũ (các thiết bị khác bị đăng xuất).
    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    refreshTokenService.revokeAll(user.getId());
    // Bước 6: cấp cặp token mới cho thiết bị hiện tại. Token mới được tạo SAU khi thu hồi nên không bị thu hồi theo.
    return issueTokens(user);
  }

  // Dùng chung cho register, login, refresh: dựng phản hồi gồm cặp token và thông tin người dùng.
  private AuthResponse issueTokens(User user) {
    return new AuthResponse(
        jwtService.generateAccessToken(user), // access token (JWT, sống ngắn)
        refreshTokenService.issue(user), // refresh token (chuỗi ngẫu nhiên, hash lưu DB)
        jwtService.accessExpiresInSeconds(), // access token sống bao nhiêu giây
        userMapper.toSummary(user)); // thông tin người dùng (không có mật khẩu)
  }

  // Lỗi 400 khi email đã được dùng.
  private static BusinessException emailTaken() {
    return BusinessException.validation(Emails.TAKEN_MESSAGE);
  }
}
