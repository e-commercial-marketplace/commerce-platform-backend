package com.marketplace.service.impl;

import com.marketplace.dto.response.CategorySummaryResponse;
import com.marketplace.mapper.ProductMapper;
import com.marketplace.repository.CategoryRepository;
import com.marketplace.service.CategoryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

  private final CategoryRepository categoryRepository;
  private final ProductMapper productMapper;

  @Override
  @Transactional(readOnly = true)
  public List<CategorySummaryResponse> listAll() {
    return productMapper.toCategorySummaries(categoryRepository.findAllByOrderByNameAsc());
  }
}
