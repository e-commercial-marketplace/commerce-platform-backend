package com.marketplace.mapper;

import com.marketplace.dto.response.CategorySummaryResponse;
import com.marketplace.dto.response.ProductResponse;
import com.marketplace.dto.response.ProductSummaryResponse;
import com.marketplace.entity.Category;
import com.marketplace.entity.Product;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Entity -> response cho catalog; {@code sellerId} lấy từ quan hệ seller. */
@Mapper(componentModel = "spring")
public interface ProductMapper {

  CategorySummaryResponse toCategorySummary(Category category);

  List<CategorySummaryResponse> toCategorySummaries(List<Category> categories);

  @Mapping(target = "sellerId", source = "seller.id")
  ProductSummaryResponse toSummary(Product product);

  @Mapping(target = "sellerId", source = "seller.id")
  ProductResponse toResponse(Product product);
}
