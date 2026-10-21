package com.marketplace.service;

/** Gửi email giao dịch cho người dùng. */
public interface EmailService {

  /**
   * Gửi mã đặt lại mật khẩu.
   *
   * @throws org.springframework.mail.MailException khi SMTP lỗi; nơi gọi tự quyết định xử lý
   */
  void sendPasswordResetCode(String to, String code, long validMinutes);
}
