package com.marketplace.service.impl;

import com.marketplace.dto.request.UserProfileUpdateRequest;
import com.marketplace.dto.response.UserProfileResponse;
import com.marketplace.entity.User;
import com.marketplace.mapper.UserMapper;
import com.marketplace.repository.UserRepository;
import com.marketplace.service.UserService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
  public UserProfileResponse updateProfile(UUID userId, UserProfileUpdateRequest request) {
    // Bước 1: user lấy theo id trong token, không bao giờ theo dữ liệu client gửi.
    User user = userRepository.getRequired(userId);
    // Bước 2: tên (nếu gửi) đã được request kiểm tra không rỗng; ở đây chỉ cắt khoảng trắng.
    if (request.name() != null) {
      user.setFullName(request.name().trim());
    }
    // Bước 3: liên hệ dùng chung quy tắc với admin: null giữ nguyên, chuỗi rỗng xoá, số điện thoại chuẩn hoá.
    user.updateContact(request.phone(), request.address());
    // Bước 4: flush để updatedAt (@PreUpdate) có giá trị mới, rồi trả hồ sơ sau khi sửa.
    return userMapper.toProfile(userRepository.saveAndFlush(user));
  }

}
