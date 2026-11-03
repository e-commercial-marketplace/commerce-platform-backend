package com.marketplace.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình gửi email, bind từ {@code app.mail.*}.
 *
 * @param enabled {@code false}: không gửi, chỉ ghi mã ra log (chỉ dùng khi phát triển local)
 * @param from địa chỉ người gửi; phải đã được xác minh trên Brevo
 * @param brevoApiKey khoá API của Brevo; chỉ nằm ở biến môi trường, không ghi ra log
 */
@ConfigurationProperties(prefix = "app.mail")
public record AppMailProperties(boolean enabled, String from, String brevoApiKey) {}
