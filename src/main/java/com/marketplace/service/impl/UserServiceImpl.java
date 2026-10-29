package com.marketplace.service.impl;

import com.marketplace.common.util.VietnamesePhone;
import com.marketplace.config.CacheConfig;
import com.marketplace.dto.request.UserProfileUpdateRequest;
import com.marketplace.dto.response.UserProfileResponse;
import com.marketplace.entity.User;
import com.marketplace.enums.Role;
import com.marketplace.enums.SellerStatus;
import com.marketplace.mapper.UserMapper;
import com.marketplace.repository.UserRepository;
import com.marketplace.service.UserService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

  private final UserRepository userRepository;
  private final UserMapper userMapper;

  @Override
  @Transactional(readOnly = true)
  public UserProfileResponse getProfile(UUID userId) {
    return userMapper.toProfile(userRepository.getRequired(userId));
  }

  @Override
  @Transactional
  // Tên shop là tên seller: đổi tên thì xoá cache trang shop (user không phải seller thì không có cache).
  @CacheEvict(cacheNames = CacheConfig.SELLER_SHOPS, key = "#userId")
  public UserProfileResponse updateProfile(UUID userId, UserProfileUpdateRequest request) {
    // Bước 1: user lấy theo id trong token, không bao giờ theo dữ liệu client gửi.
    User user = userRepository.getRequired(userId);
    // Bước 2: tên (nếu gửi) đã được request kiểm tra không rỗng; ở đây chỉ cắt khoảng trắng.
    if (request.name() != null) {
      user.setFullName(request.name().trim());
    }
    // Bước 3: liên hệ dùng chung quy tắc với admin: null giữ nguyên, chuỗi rỗng xoá, số điện thoại chuẩn hoá.
    updateContact(user, request.phone(), request.address());
    // Bước 4: flush để updatedAt (@PreUpdate) có giá trị mới, rồi trả hồ sơ sau khi sửa.
    return userMapper.toProfile(userRepository.saveAndFlush(user));
  }

  @Override
  public User buildNewUser(String email, String passwordHash, String fullName, Role role) {
    return User.builder()
        .email(email)
        .passwordHash(passwordHash)
        .fullName(fullName)
        .role(role)
        .sellerStatus(role == Role.SELLER ? SellerStatus.PENDING : null)
        .build();
  }

  @Override
  public void updateContact(User user, String phone, String address) {
    if (phone != null) {
      user.setPhone(VietnamesePhone.normalize(phone));
    }
    if (address != null) {
      String trimmed = address.trim();
      user.setAddress(trimmed.isEmpty() ? null : trimmed);
    }
  }
}
