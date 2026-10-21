package com.marketplace.mapper;

import com.marketplace.dto.response.UserResponse;
import com.marketplace.dto.response.UserSummaryResponse;
import com.marketplace.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Đổi {@link User} sang response. MapStruct sinh code lúc biên dịch: trường cùng tên tự map, chỉ cần
 * khai báo trường khác tên. Không response nào có password hash, nên hash không bao giờ lộ ra API.
 */
@Mapper(componentModel = "spring") // Spring tạo bean, service chỉ cần inject UserMapper
public interface UserMapper {

  // Entity lưu tên ở fullName (cột full_name), API trả về trường name.
  @Mapping(target = "name", source = "fullName")
  UserSummaryResponse toSummary(User user);

  @Mapping(target = "name", source = "fullName")
  UserResponse toResponse(User user);
}
