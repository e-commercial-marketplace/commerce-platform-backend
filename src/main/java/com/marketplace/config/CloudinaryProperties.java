package com.marketplace.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình {@code app.cloudinary.*}. Để trống thì tắt upload ảnh: API ký trả 503 và không kiểm tra URL ảnh.
 *
 * @param cloudName tên cloud, nằm trong mọi URL ảnh của tài khoản
 * @param apiKey khoá công khai, được gửi cho client
 * @param apiSecret khoá bí mật dùng để ký, không bao giờ gửi cho client
 */
@ConfigurationProperties(prefix = "app.cloudinary")
public record CloudinaryProperties(String cloudName, String apiKey, String apiSecret) {}
