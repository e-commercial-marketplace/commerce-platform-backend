package com.marketplace.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình cổng VNPAY, bind từ {@code app.vnpay.*}. Mã và khoá bí mật lấy từ email VNPAY gửi khi đăng
 * ký merchant sandbox; chỉ nằm ở backend, không bao giờ gửi cho client.
 *
 * @param tmnCode mã website (vnp_TmnCode)
 * @param hashSecret khoá bí mật để ký HMAC-SHA512 (vnp_HashSecret)
 * @param payUrl trang thanh toán của VNPAY (sandbox hoặc production)
 * @param returnUrl URL backend nhận trình duyệt quay về sau khi thanh toán (vnp_ReturnUrl)
 * @param appRedirectUrl nơi backend chuyển tiếp trình duyệt sau khi xử lý Return URL (deep link của app)
 * @param expireMinutes link thanh toán hết hạn sau bao nhiêu phút (vnp_ExpireDate)
 */
@ConfigurationProperties(prefix = "app.vnpay")
public record VnpayProperties(
    String tmnCode,
    String hashSecret,
    String payUrl,
    String returnUrl,
    String appRedirectUrl,
    int expireMinutes) {}
