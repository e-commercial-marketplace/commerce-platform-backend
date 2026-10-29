package com.marketplace.controller;

import com.marketplace.common.response.ApiResponse;
import com.marketplace.dto.response.CategorySummaryResponse;
import com.marketplace.service.CategoryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Danh sách category công khai; không cần token. */
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

  private final CategoryService categoryService;

  @GetMapping
  public ResponseEntity<ApiResponse<List<CategorySummaryResponse>>> list() {
    return ResponseEntity.ok(ApiResponse.ok(categoryService.listCategories()));
  }
}
