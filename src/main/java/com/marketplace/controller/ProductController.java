package com.marketplace.controller;

import com.marketplace.common.response.ApiResponse;
import com.marketplace.common.response.PageResponse;
import com.marketplace.dto.request.ProductSearchRequest;
import com.marketplace.dto.response.ProductResponse;
import com.marketplace.dto.response.ProductSummaryResponse;
import com.marketplace.service.ProductService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Duyệt product công khai; không cần token. URL thật là {@code /api/v1/products/...}. */
@Validated
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

  private final ProductService productService;

  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<ProductSummaryResponse>>> search(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) UUID categoryId,
      @RequestParam(required = false) UUID sellerId, // xem shop: chỉ product đang bán của seller này
      @RequestParam(required = false) @DecimalMin("0") BigDecimal minPrice,
      @RequestParam(required = false) @DecimalMin("0") BigDecimal maxPrice,
      @RequestParam(required = false) @DecimalMin("0") @DecimalMax("5") BigDecimal minRating,
      @RequestParam(required = false) String sort,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    ProductSearchRequest request =
        new ProductSearchRequest(
            q, categoryId, sellerId, minPrice, maxPrice, minRating, sort, page, size);
    return ResponseEntity.ok(ApiResponse.ok(productService.search(request)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<ProductResponse>> getById(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.ok(productService.getActiveProduct(id)));
  }
}
