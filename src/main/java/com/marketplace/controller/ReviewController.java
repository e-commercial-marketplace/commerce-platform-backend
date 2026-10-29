package com.marketplace.controller;

import com.marketplace.common.response.ApiResponse;
import com.marketplace.common.response.PageResponse;
import com.marketplace.dto.request.ReviewCreateRequest;
import com.marketplace.dto.response.ReviewResponse;
import com.marketplace.security.AuthUser;
import com.marketplace.service.ReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Đánh giá sản phẩm. URL thật là {@code /api/v1/products/{productId}/reviews}: GET công khai (luật
 * PUBLIC_GET), POST chỉ CUSTOMER (luật CUSTOMER_ONLY). Không có sửa hay xoá đánh giá.
 */
@Validated
@RestController
@RequestMapping("/products/{productId}/reviews")
@RequiredArgsConstructor
public class ReviewController {

  private final ReviewService reviewService;

  @PostMapping
  public ResponseEntity<ApiResponse<ReviewResponse>> create(
      @AuthenticationPrincipal AuthUser me,
      @PathVariable UUID productId,
      @Valid @RequestBody ReviewCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(reviewService.createReview(me.id(), productId, request)));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> list(
      @PathVariable UUID productId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return ResponseEntity.ok(ApiResponse.ok(reviewService.listProductReviews(productId, page, size)));
  }
}
