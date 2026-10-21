package com.marketplace.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bật/tắt gửi email thật, bind từ {@code app.mail.*}. Thông tin SMTP (host, tài khoản, App Password)
 * nằm ở {@code spring.mail.*} và do Spring Boot tự cấu hình {@code JavaMailSender}.
 *
 * @param enabled {@code false}: không gửi, chỉ ghi mã ra log (chỉ dùng khi phát triển local)
 * @param from địa chỉ người gửi; với Gmail phải trùng tài khoản đăng nhập SMTP
 */
@ConfigurationProperties(prefix = "app.mail")
public record AppMailProperties(boolean enabled, String from) {}
