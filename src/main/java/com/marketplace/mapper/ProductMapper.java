package com.marketplace.mapper;

import com.marketplace.dto.response.ProductResponse;
import com.marketplace.dto.response.ProductSummaryResponse;
import com.marketplace.entity.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Entity -> response cho catalog; {@code sellerId} lấy từ quan hệ seller. */
@Mapper(componentModel = "spring", uses = CategoryMapper.class) // category lồng bên trong
public interface ProductMapper {

  @Mapping(target = "sellerId", source = "seller.id")
  ProductSummaryResponse toSummary(Product product);

  @Mapping(target = "sellerId", source = "seller.id")
  ProductResponse toResponse(Product product);
}
