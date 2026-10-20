package com.marketplace.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình JWT bind từ {@code app.jwt.*} trong {@code application.yml}.
 *
 * @param secret khoá HMAC, tối thiểu 32 byte (thiếu thì fail-fast lúc khởi động)
 * @param accessExpirationMs thời hạn access token (mặc định 15 phút)
 * @param refreshExpirationMs thời hạn refresh token (mặc định 7 ngày)
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long accessExpirationMs, long refreshExpirationMs) {}
