package com.marketplace.dto.request;

import com.marketplace.common.util.VietnamesePhone;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body của {@code PATCH /users/me}: người dùng tự sửa hồ sơ. Trường {@code null} thì giữ nguyên; với
 * {@code phone} và {@code address}, chuỗi rỗng nghĩa là xoá.
 *
 * @param name nếu gửi thì phải có ít nhất một ký tự không phải khoảng trắng
 * @param phone số di động Việt Nam ({@code 0} hoặc {@code +84} và 9 chữ số), hoặc chuỗi rỗng để xoá
 * @param address tối đa 500 ký tự, hoặc chuỗi rỗng để xoá
 */
public record UserProfileUpdateRequest(
    @Size(max = 255) @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") String name,
    @Pattern(regexp = VietnamesePhone.PATTERN, message = "must be a Vietnamese mobile number") String phone,
    @Size(max = 500) String address) {}
