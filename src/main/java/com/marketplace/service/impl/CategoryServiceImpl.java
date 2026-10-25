package com.marketplace.service.impl;

import com.marketplace.common.exception.BusinessException;
import com.marketplace.dto.request.CategoryRequest;
import com.marketplace.dto.response.CategorySummaryResponse;
import com.marketplace.entity.Category;
import com.marketplace.mapper.CategoryMapper;
import com.marketplace.repository.CategoryRepository;
import com.marketplace.repository.ProductRepository;
import com.marketplace.service.CategoryService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service // Spring tạo và quản lý class nghiệp vụ này
@RequiredArgsConstructor // Lombok tạo constructor nhận 3 field final bên dưới
public class CategoryServiceImpl implements CategoryService {

  private final CategoryRepository categoryRepository; // đọc/ghi category
  private final ProductRepository productRepository; // kiểm tra category còn product không
  private final CategoryMapper categoryMapper; // đổi Category thành CategorySummaryResponse

  @Override
  @Transactional(readOnly = true) // chỉ đọc nên khai báo readOnly để nhẹ hơn
  public List<CategorySummaryResponse> listAll() {
    return categoryMapper.toSummaries(categoryRepository.findAllByOrderByNameAsc());
  }

  @Override
  @Transactional
  public CategorySummaryResponse create(CategoryRequest request) {
    // Bước 1: bỏ khoảng trắng hai đầu, vì "Electronics " và "Electronics" là cùng một tên.
    String name = request.name().trim();
    // Bước 2: tên đã có (không phân biệt hoa thường) thì từ chối.
    if (categoryRepository.existsByNameIgnoreCase(name)) {
      throw duplicateName();
    }
    try {
      // Bước 3: lưu ngay (flush) để nếu hai request tạo cùng tên một lúc thì lỗi unique bật ra tại đây.
      return categoryMapper.toSummary(categoryRepository.saveAndFlush(Category.builder().name(name).build()));
    } catch (DataIntegrityViolationException e) {
      throw duplicateName();
    }
  }

  @Override
  @Transactional
  public CategorySummaryResponse rename(UUID id, CategoryRequest request) {
    // Bước 1: category phải tồn tại, nếu không 404.
    Category category = findCategory(id);
    String name = request.name().trim();
    // Bước 2: tên không được trùng với category KHÁC (đổi thành chính tên của nó thì được phép).
    if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
      throw duplicateName();
    }
    category.setName(name);
    try {
      // Bước 3: ghi ngay để bắt lỗi unique do hai request đổi tên chạy song song.
      return categoryMapper.toSummary(categoryRepository.saveAndFlush(category));
    } catch (DataIntegrityViolationException e) {
      throw duplicateName();
    }
  }

  @Override
  @Transactional
  public void delete(UUID id) {
    Category category = findCategory(id);
    // Category còn product (kể cả product đã ẩn) thì không được xoá, vì product còn tham chiếu tới nó.
    if (productRepository.existsByCategoryId(id)) {
      throw BusinessException.validation("Category still has products");
    }
    categoryRepository.delete(category);
  }

  /** Tìm category theo id, không có thì 404 RESOURCE_NOT_FOUND. */
  private Category findCategory(UUID id) {
    return categoryRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Category", id));
  }

  /** Lỗi 400 khi tên category bị trùng. */
  private static BusinessException duplicateName() {
    return BusinessException.validation("Category name already exists");
  }
}
