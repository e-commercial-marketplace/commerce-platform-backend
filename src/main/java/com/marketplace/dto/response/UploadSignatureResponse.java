package com.marketplace.dto.response;

/**
 * Mọi thứ client cần để gửi file thẳng lên Cloudinary. Các tham số phải gửi đúng nguyên giá trị, vì đều nằm trong
 * chuỗi đã ký.
 *
 * @param uploadUrl địa chỉ gửi file (POST multipart/form-data)
 * @param apiKey tham số {@code api_key}
 * @param timestamp tham số {@code timestamp} (giây); chữ ký hết hạn sau một giờ
 * @param publicId tham số {@code public_id}, do backend sinh
 * @param allowedFormats tham số {@code allowed_formats}
 * @param overwrite tham số {@code overwrite}, luôn {@code false}
 * @param signature tham số {@code signature}
 */
public record UploadSignatureResponse(
    String uploadUrl,
    String apiKey,
    long timestamp,
    String publicId,
    String allowedFormats,
    boolean overwrite,
    String signature) {}
