package com.marketplace.service.impl;

import com.marketplace.common.exception.BusinessException;
import com.marketplace.common.response.PageResponse;
import com.marketplace.common.util.Emails;
import com.marketplace.common.util.Pages;
import com.marketplace.dto.request.AdminUserCreateRequest;
import com.marketplace.dto.request.AdminUserUpdateRequest;
import com.marketplace.dto.response.UserResponse;
import com.marketplace.entity.User;
import com.marketplace.enums.Role;
import com.marketplace.enums.SellerStatus;
import com.marketplace.mapper.UserMapper;
import com.marketplace.repository.UserRepository;
import com.marketplace.repository.UserSpecifications;
import com.marketplace.service.AdminUserService;
import com.marketplace.service.RefreshTokenService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Admin quản lý user. Tài khoản không bị xoá mà bị vô hiệu hoá ({@code enabled = false}) để product,
 * order và review tham chiếu tới user vẫn hợp lệ. Admin không được tự khoá hoặc tự hạ role của mình,
 * nếu không hệ thống có thể không còn ai quản trị.
 */
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final RefreshTokenService refreshTokenService;
  private final UserMapper userMapper;

  @Override
  @Transactional(readOnly = true)
  public PageResponse<UserResponse> list(Role role, String search, int page, int size) {
    Specification<User> spec = Specification.where(null);
    if (role != null) {
      spec = spec.and(UserSpecifications.hasRole(role));
    }
    if (StringUtils.hasText(search)) {
      spec = spec.and(UserSpecifications.emailOrNameContains(search.trim()));
    }
    return PageResponse.from(
        userRepository.findAll(spec, Pages.newestFirst(page, size)), userMapper::toResponse);
  }

  @Override
  @Transactional(readOnly = true)
  public UserResponse get(UUID id) {
    return userMapper.toResponse(userRepository.getRequired(id));
  }

  @Override
  @Transactional
  public UserResponse create(AdminUserCreateRequest request) {
    String email = Emails.normalize(request.email());
    if (userRepository.existsByEmailIgnoreCase(email)) {
      throw emailTaken();
    }
    // User.register tự đặt SELLER ở trạng thái PENDING; admin duyệt sau bằng endpoint approve.
    User user =
        User.register(
            email, passwordEncoder.encode(request.password()), request.name().trim(), request.role());
    user.setEnabled(request.enabled() == null || request.enabled());
    user.updateContact(request.phone(), request.address()); // admin nhập sẵn liên hệ (tuỳ chọn)
    try {
      return userMapper.toResponse(userRepository.saveAndFlush(user));
    } catch (DataIntegrityViolationException e) {
      throw emailTaken(); // hai request tạo cùng email song song, request này thua
    }
  }

  @Override
  @Transactional
  public UserResponse update(UUID id, AdminUserUpdateRequest request, UUID actorId) {
    User user = userRepository.getRequired(id);
    Role role = request.role() != null ? request.role() : user.getRole();
    if (user.getId().equals(actorId)
        && (role != user.getRole() || Boolean.FALSE.equals(request.enabled()))) {
      throw selfLockout();
    }
    // Tính sellerStatus trước khi đổi role, vì cần so với role hiện tại.
    SellerStatus sellerStatus = resolveSellerStatus(user, role, request.sellerStatus());

    if (request.name() != null) {
      user.setFullName(request.name().trim()); // @NullOrNotBlank ở request đã chặn tên toàn khoảng trắng
    }
    user.updateContact(request.phone(), request.address()); // admin có toàn quyền sửa liên hệ
    user.setRole(role);
    user.setSellerStatus(sellerStatus);
    if (request.enabled() != null) {
      changeEnabled(user, request.enabled());
    }
    return userMapper.toResponse(userRepository.saveAndFlush(user));
  }

  @Override
  @Transactional
  public void disable(UUID id, UUID actorId) {
    User user = userRepository.getRequired(id);
    if (user.getId().equals(actorId)) {
      throw selfLockout();
    }
    changeEnabled(user, false);
    userRepository.save(user);
  }

  @Override
  @Transactional
  public UserResponse approveSeller(UUID id) {
    User seller = findSeller(id);
    if (seller.getSellerStatus() == SellerStatus.APPROVED) {
      throw BusinessException.validation("Seller is already approved");
    }
    seller.setSellerStatus(SellerStatus.APPROVED);
    return userMapper.toResponse(userRepository.saveAndFlush(seller));
  }

  @Override
  @Transactional
  public UserResponse suspendSeller(UUID id) {
    User seller = findSeller(id);
    if (seller.getSellerStatus() != SellerStatus.APPROVED) {
      throw BusinessException.validation("Only an approved seller can be suspended");
    }
    seller.setSellerStatus(SellerStatus.SUSPENDED);
    return userMapper.toResponse(userRepository.saveAndFlush(seller));
  }


  /** Tìm user phải là seller: không tồn tại thì 404, tồn tại nhưng không phải seller thì 400. */
  private User findSeller(UUID id) {
    User user = userRepository.getRequired(id);
    if (user.getRole() != Role.SELLER) {
      throw BusinessException.validation("User is not a seller");
    }
    return user;
  }

  /**
   * sellerStatus chỉ có ý nghĩa với SELLER. Rời khỏi SELLER thì xoá về null; thành SELLER mà không nói
   * rõ thì PENDING; đã là SELLER mà không nói gì thì giữ nguyên.
   */
  private static SellerStatus resolveSellerStatus(User user, Role role, SellerStatus requested) {
    if (role != Role.SELLER) {
      if (requested != null) {
        throw BusinessException.validation("sellerStatus can only be set on a SELLER");
      }
      return null;
    }
    if (requested != null) {
      return requested;
    }
    return user.getSellerStatus() != null ? user.getSellerStatus() : SellerStatus.PENDING;
  }

  /** Khoá tài khoản thì thu hồi luôn refresh token để user không đổi lấy access token mới được nữa. */
  private void changeEnabled(User user, boolean enabled) {
    user.setEnabled(enabled);
    if (!enabled) {
      refreshTokenService.revokeAll(user.getId());
    }
  }

  private static BusinessException emailTaken() {
    return BusinessException.validation(Emails.TAKEN_MESSAGE);
  }

  private static BusinessException selfLockout() {
    return BusinessException.validation("Admin cannot disable or change the role of their own account");
  }
}
