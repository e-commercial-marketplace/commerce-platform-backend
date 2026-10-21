package com.marketplace.service.impl;

import com.marketplace.common.exception.BusinessException;
import com.marketplace.common.util.Emails;
import com.marketplace.common.util.TimeProvider;
import com.marketplace.config.PasswordResetProperties;
import com.marketplace.dto.request.ForgotPasswordRequest;
import com.marketplace.dto.request.ResetPasswordRequest;
import com.marketplace.entity.PasswordResetCode;
import com.marketplace.entity.User;
import com.marketplace.repository.PasswordResetCodeRepository;
import com.marketplace.repository.UserRepository;
import com.marketplace.service.EmailService;
import com.marketplace.service.PasswordResetService;
import com.marketplace.service.RefreshTokenService;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quên mật khẩu bằng mã 6 chữ số gửi qua email. Quy tắc theo features/auth/API_CONTRACT.md:
 * mã sống 15 phút, sai tối đa 5 lần, dùng một lần; mỗi user xin mã mới cách nhau ít nhất 60 giây
 * và tối đa 5 mã mỗi giờ. DB chỉ lưu hash BCrypt của mã.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

  // Bộ sinh số ngẫu nhiên an toàn cho mật mã: mã không đoán trước được.
  private static final SecureRandom RANDOM = new SecureRandom();
  // Một thông báo chung cho mọi trường hợp mã không dùng được, để không lộ lý do cụ thể.
  private static final String INVALID_CODE = "Invalid or expired reset code";

  private final UserRepository userRepository;
  private final PasswordResetCodeRepository codeRepository;
  private final PasswordEncoder passwordEncoder; // BCrypt: băm mã và mật khẩu mới
  private final EmailService emailService;
  private final RefreshTokenService refreshTokenService;
  private final PasswordResetProperties props;
  private final TimeProvider timeProvider;

  @Override
  @Transactional
  public void requestReset(ForgotPasswordRequest request) {
    // Bước 1: tìm tài khoản. Không có hoặc đã bị khoá thì dừng im lặng: response vẫn y như khi thành công.
    User user = userRepository.findByEmailIgnoreCase(Emails.normalize(request.email())).orElse(null);
    // Log chỉ nằm ở server (không trả cho client) để người vận hành biết vì sao không có email.
    if (user == null) {
      log.info("Password reset requested for {}: no account, no email sent", mask(request.email()));
      return;
    }
    if (!user.isEnabled()) {
      log.info("Password reset requested for disabled user {}: no email sent", user.getId());
      return;
    }
    Instant now = timeProvider.now();
    // Bước 2: chống spam email và dò mã. Vượt giới hạn thì cũng dừng im lặng.
    if (!canIssueNewCode(user.getId(), now)) {
      log.info("Password reset for user {} skipped: cooldown or hourly limit reached", user.getId());
      return;
    }
    // Bước 3: sinh mã 6 chữ số (000000 đến 999999), giữ số 0 ở đầu.
    String code = String.format("%06d", RANDOM.nextInt(1_000_000));
    // Bước 4: mã mới thay mã cũ (vô hiệu mã chưa dùng), rồi lưu HASH của mã mới, không lưu mã thô.
    codeRepository.invalidateAll(user.getId(), now);
    codeRepository.save(
        PasswordResetCode.issue(
            user,
            passwordEncoder.encode(code),
            now,
            now.plus(Duration.ofMinutes(props.codeTtlMinutes()))));
    // Bước 5: gửi email. Lỗi gửi chỉ ghi log, không ném ra ngoài, để response không lộ email có tồn tại.
    // Người dùng xin lại mã sau 60 giây nếu không nhận được.
    try {
      emailService.sendPasswordResetCode(user.getEmail(), code, props.codeTtlMinutes());
      log.info("Password reset code issued for user {}", user.getId());
    } catch (MailException e) {
      log.error("Could not send the password reset email to user {}", user.getId(), e);
    }
  }

  // noRollbackFor: khi nhập sai mã, hàm ném lỗi 400 nhưng vẫn phải LƯU số lần sai đã tăng.
  // Mặc định Spring hoàn tác transaction khi có RuntimeException, nên phải tắt riêng cho BusinessException.
  @Override
  @Transactional(noRollbackFor = BusinessException.class)
  public void resetPassword(ResetPasswordRequest request) {
    // Bước 1: tài khoản phải tồn tại và đang hoạt động; nếu không thì cùng lỗi như mã sai.
    User user =
        userRepository
            .findByEmailIgnoreCase(Emails.normalize(request.email()))
            .filter(User::isEnabled)
            .orElseThrow(this::invalidCode);
    Instant now = timeProvider.now();
    // Bước 2: lấy mã chưa dùng mới nhất; phải còn hạn và còn lượt thử.
    PasswordResetCode code =
        codeRepository
            .findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(user.getId())
            .filter(c -> c.isUsable(now, props.maxAttempts()))
            .orElseThrow(this::invalidCode);
    // Bước 3: so mã người dùng nhập với hash. Sai thì tăng số lần sai (được lưu nhờ noRollbackFor) rồi báo lỗi.
    if (!passwordEncoder.matches(request.code(), code.getCodeHash())) {
      code.recordFailedAttempt();
      throw invalidCode();
    }
    // Bước 4: đúng mã. Đổi mật khẩu (lưu hash), vô hiệu mọi mã còn lại để mã này không dùng lại được.
    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    codeRepository.invalidateAll(user.getId(), now);
    // Bước 5: đăng xuất mọi thiết bị: refresh token cũ không đổi được token mới nữa.
    refreshTokenService.revokeAll(user.getId());
  }

  /** Được cấp mã mới khi: mã gần nhất đã qua thời gian chờ, và số mã trong một giờ chưa đủ giới hạn. */
  private boolean canIssueNewCode(UUID userId, Instant now) {
    boolean coolingDown =
        codeRepository
            .findFirstByUserIdOrderByCreatedAtDesc(userId)
            .map(last -> last.getCreatedAt().isAfter(now.minusSeconds(props.resendCooldownSeconds())))
            .orElse(false);
    long issuedLastHour = codeRepository.countByUserIdAndCreatedAtAfter(userId, now.minus(Duration.ofHours(1)));
    return !coolingDown && issuedLastHour < props.maxCodesPerHour();
  }

  /** Che email trong log: "alice@example.com" thành "a***@example.com". */
  private static String mask(String email) {
    int at = email.indexOf('@');
    return at <= 1 ? "***" : email.charAt(0) + "***" + email.substring(at);
  }

  private BusinessException invalidCode() {
    return BusinessException.validation(INVALID_CODE);
  }
}
