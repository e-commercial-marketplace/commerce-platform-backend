package com.marketplace.controller;

import com.marketplace.common.response.ApiResponse;
import com.marketplace.common.response.PageResponse;
import com.marketplace.dto.request.ProductCreateRequest;
import com.marketplace.dto.request.ProductUpdateRequest;
import com.marketplace.dto.request.StockUpdateRequest;
import com.marketplace.dto.response.ProductResponse;
import com.marketplace.dto.response.ProductSummaryResponse;
import com.marketplace.security.AuthUser;
import com.marketplace.service.SellerProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quản lý product của seller. URL thật là {@code /api/v1/seller/products/...}; role SELLER được chặn
 * ở {@code SecurityConfig}, còn trạng thái APPROVED và quyền sở hữu được kiểm tra trong service.
 */
@Validated // bật kiểm tra @Min/@Max trên các tham số truy vấn (page, size) của hàm list
@RestController
@RequestMapping("/seller/products")
@RequiredArgsConstructor
public class SellerProductController {

  private final SellerProductService sellerProductService;

  // Danh sách product của CHÍNH seller (me.id() từ token), kể cả product đã ẩn.
  // active=true chỉ lấy đang bán, active=false chỉ lấy đã ẩn, bỏ trống thì lấy cả hai.
  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<ProductSummaryResponse>>> list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Boolean active,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @AuthenticationPrincipal AuthUser me) {
    return ResponseEntity.ok(
        ApiResponse.ok(sellerProductService.list(me.id(), q, active, page, size)));
  }

  // Chi tiết một product của chính seller: 404 nếu không có, 403 nếu của seller khác.
  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<ProductResponse>> get(
      @PathVariable UUID id, @AuthenticationPrincipal AuthUser me) {
    return ResponseEntity.ok(ApiResponse.ok(sellerProductService.get(me.id(), id)));
  }

  // @AuthenticationPrincipal AuthUser me: người đang đăng nhập, lấy từ token. Luôn dùng me.id() làm
  // sellerId, không bao giờ nhận sellerId từ client.
  @PostMapping
  public ResponseEntity<ApiResponse<ProductResponse>> create(
      @Valid @RequestBody ProductCreateRequest request, @AuthenticationPrincipal AuthUser me) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(sellerProductService.create(me.id(), request)));
  }

  @PatchMapping("/{id}")
  public ResponseEntity<ApiResponse<ProductResponse>> update(
      @PathVariable UUID id,
      @Valid @RequestBody ProductUpdateRequest request,
      @AuthenticationPrincipal AuthUser me) {
    return ResponseEntity.ok(ApiResponse.ok(sellerProductService.update(me.id(), id, request)));
  }

  // 204 No Content: thành công và không có body.
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal AuthUser me) {
    sellerProductService.delete(me.id(), id);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/{id}/stock")
  public ResponseEntity<ApiResponse<ProductResponse>> updateStock(
      @PathVariable UUID id,
      @Valid @RequestBody StockUpdateRequest request,
      @AuthenticationPrincipal AuthUser me) {
    return ResponseEntity.ok(ApiResponse.ok(sellerProductService.updateStock(me.id(), id, request)));
  }
}
