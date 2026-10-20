package com.marketplace.controller;

import com.marketplace.common.response.ApiResponse;
import com.marketplace.dto.request.CategoryRequest;
import com.marketplace.dto.response.CategorySummaryResponse;
import com.marketplace.service.CategoryService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quản lý category của admin. URL thật là {@code /api/v1/admin/categories/...}; role ADMIN được chặn
 * ở {@code SecurityConfig} (luật {@code /api/v1/admin/**}).
 */
@RestController
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

  private final CategoryService categoryService;

  @PostMapping
  public ResponseEntity<ApiResponse<CategorySummaryResponse>> create(
      @Valid @RequestBody CategoryRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(categoryService.create(request)));
  }

  @PatchMapping("/{id}")
  public ResponseEntity<ApiResponse<CategorySummaryResponse>> rename(
      @PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(categoryService.rename(id, request)));
  }

  // 204 No Content: xoá thành công và không có body.
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    categoryService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
