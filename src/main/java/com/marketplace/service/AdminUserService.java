package com.marketplace.service;

import com.marketplace.common.response.PageResponse;
import com.marketplace.dto.request.AdminUserCreateRequest;
import com.marketplace.dto.request.AdminUserUpdateRequest;
import com.marketplace.dto.response.UserResponse;
import com.marketplace.enums.Role;
import java.util.UUID;

/** Quản lý user và duyệt seller dành cho admin. */
public interface AdminUserService {

  /** Danh sách user mới nhất trước; {@code role} và {@code search} (email hoặc tên) đều tuỳ chọn. */
  PageResponse<UserResponse> listUsers(Role role, String search, int page, int size);

  UserResponse getUser(UUID id);

  UserResponse createUser(AdminUserCreateRequest request);

  /** Cập nhật một phần; admin không được tự vô hiệu hoá hoặc tự đổi role của mình. */
  UserResponse updateUser(UUID id, AdminUserUpdateRequest request, UUID actorId);

  /** Vô hiệu hoá tài khoản (không xoá dòng) và thu hồi mọi refresh token của user. */
  void disableUser(UUID id, UUID actorId);

  /** Seller chưa duyệt, bị từ chối hoặc bị khoá sang {@code APPROVED}. */
  UserResponse approveSeller(UUID id);

  /** Seller đang {@code APPROVED} sang {@code SUSPENDED}. */
  UserResponse suspendSeller(UUID id);
}
