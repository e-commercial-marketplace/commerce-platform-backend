package com.marketplace.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Quy tắc mã đặt lại mật khẩu, bind từ {@code app.password-reset.*} trong {@code application.yml}.
 *
 * @param codeTtlMinutes mã hết hạn sau bao nhiêu phút (mặc định 15)
 * @param maxAttempts số lần nhập sai tối đa cho một mã (mặc định 5)
 * @param resendCooldownSeconds phải chờ bao lâu mới được xin mã mới (mặc định 60 giây)
 * @param maxCodesPerHour số mã tối đa một user được cấp trong một giờ (mặc định 5)
 */
@ConfigurationProperties(prefix = "app.password-reset")
public record PasswordResetProperties(
    long codeTtlMinutes, int maxAttempts, long resendCooldownSeconds, int maxCodesPerHour) {}
