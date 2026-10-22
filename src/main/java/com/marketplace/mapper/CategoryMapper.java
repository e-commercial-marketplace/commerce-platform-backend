package com.marketplace.mapper;

import com.marketplace.dto.response.CategorySummaryResponse;
import com.marketplace.entity.Category;
import java.util.List;
import org.mapstruct.Mapper;

/** Đổi {@link Category} sang response; ProductMapper cũng dùng mapper này cho trường category lồng bên trong. */
@Mapper(componentModel = "spring")
public interface CategoryMapper {

  CategorySummaryResponse toSummary(Category category);

  List<CategorySummaryResponse> toSummaries(List<Category> categories);
}
