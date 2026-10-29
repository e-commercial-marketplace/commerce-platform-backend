package com.marketplace.service;

import com.marketplace.dto.request.UserProfileUpdateRequest;
import com.marketplace.dto.response.UserProfileResponse;
import com.marketplace.entity.User;
import com.marketplace.enums.Role;
import java.util.UUID;

/** Nghiệp vụ hồ sơ user. */
public interface UserService {

  /** Hồ sơ của chính user đang đăng nhập. */
  UserProfileResponse getProfile(UUID userId);

  /** Người dùng tự sửa tên, số điện thoại, địa chỉ; trường {@code null} thì giữ nguyên. */
  UserProfileResponse updateProfile(UUID userId, UserProfileUpdateRequest request);

  /**
   * User mới (chưa lưu), đang hoạt động. SELLER bắt đầu ở trạng thái PENDING chờ admin duyệt; role khác
   * không có trạng thái seller. {@code passwordHash} phải là mật khẩu đã băm.
   */
  User buildNewUser(String email, String passwordHash, String fullName, Role role);

  /**
   * Sửa liên hệ theo một quy tắc chung cho user và admin: {@code null} giữ nguyên, số điện thoại được chuẩn
   * hoá, địa chỉ cắt khoảng trắng và chuỗi rỗng thì xoá.
   */
  void updateContact(User user, String phone, String address);
}
