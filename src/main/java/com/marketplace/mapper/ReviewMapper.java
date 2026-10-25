package com.marketplace.mapper;

import com.marketplace.dto.response.ReviewResponse;
import com.marketplace.entity.Review;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Đổi {@link Review} sang response. */
@Mapper(componentModel = "spring")
public interface ReviewMapper {

  @Mapping(target = "productId", source = "product.id")
  @Mapping(target = "userId", source = "user.id") // proxy LAZY: đọc id không cần query thêm
  ReviewResponse toResponse(Review review);
}
