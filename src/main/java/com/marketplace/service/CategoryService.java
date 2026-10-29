package com.marketplace.service;

import com.marketplace.dto.request.CategoryRequest;
import com.marketplace.dto.response.CategorySummaryResponse;
import java.util.List;
import java.util.UUID;

/** Nghiệp vụ category của catalog: đọc công khai và quản lý của admin. */
public interface CategoryService {

  /** Toàn bộ category, sắp theo tên (public). */
  List<CategorySummaryResponse> listCategories();

  /** Admin tạo category. Lỗi 400 nếu tên trống hoặc đã tồn tại. */
  CategorySummaryResponse createCategory(CategoryRequest request);

  /**
   * Admin đổi tên category. Lỗi 404 nếu không có category, 400 nếu tên trống hoặc đã thuộc category
   * khác.
   */
  CategorySummaryResponse renameCategory(UUID id, CategoryRequest request);

  /** Admin xoá category. Lỗi 404 nếu không có category, 400 nếu category còn product. */
  void deleteCategory(UUID id);
}
