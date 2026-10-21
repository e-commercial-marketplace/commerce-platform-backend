package com.marketplace.service.impl;

import com.marketplace.config.AppMailProperties;
import com.marketplace.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Gửi email qua SMTP (Gmail khi phát triển). Khi {@code app.mail.enabled=false} thì không gửi mà
 * ghi mã ra log, để chạy local không cần tài khoản email. Production phải bật email, vì log không
 * được chứa mã thật.
 */
@Slf4j // Lombok tạo biến "log" để ghi log
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

  private final JavaMailSender mailSender; // Spring Boot tạo sẵn từ cấu hình spring.mail.*
  private final AppMailProperties mailProperties; // bật/tắt gửi thật và địa chỉ người gửi

  @Override
  public void sendPasswordResetCode(String to, String code, long validMinutes) {
    // Bước 1: email đang tắt (phát triển local) thì chỉ ghi ra log rồi dừng.
    if (!mailProperties.enabled()) {
      log.warn("Email is disabled (app.mail.enabled=false). Password reset code for {}: {}", to, code);
      return;
    }
    // Bước 2: soạn email dạng chữ thường (không HTML), đủ cho một mã số.
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(mailProperties.from());
    message.setTo(to);
    message.setSubject("Mã đặt lại mật khẩu");
    message.setText(
        "Mã đặt lại mật khẩu của bạn là: "
            + code
            + "\n\nMã có hiệu lực trong "
            + validMinutes
            + " phút và chỉ dùng được một lần."
            + "\nNếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này.");
    // Bước 3: gửi qua SMTP. Lỗi (sai App Password, mất mạng) ném MailException cho nơi gọi xử lý.
    mailSender.send(message);
  }
}
