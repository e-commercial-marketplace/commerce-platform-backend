package com.marketplace.controller;

import com.marketplace.common.response.ApiResponse;
import com.marketplace.common.response.PageResponse;
import com.marketplace.dto.request.OrderItemCancelRequest;
import com.marketplace.dto.response.OrderItemResponse;
import com.marketplace.enums.OrderItemStatus;
import com.marketplace.security.AuthUser;
import com.marketplace.service.SellerOrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Seller xem và xử lý dòng hàng của mình. URL thật là {@code /api/v1/seller/order-items/...}; chỉ role
 * SELLER (luật SELLER_ONLY ở SecurityConfig), trạng thái APPROVED kiểm tra trong service.
 */
@Validated
@RestController
@RequestMapping("/seller/order-items")
@RequiredArgsConstructor
public class SellerOrderController {

  private final SellerOrderService sellerOrderService;

  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<OrderItemResponse>>> list(
      @AuthenticationPrincipal AuthUser me,
      @RequestParam(required = false) OrderItemStatus status,
      @RequestParam(required = false) UUID orderId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return ResponseEntity.ok(
        ApiResponse.ok(sellerOrderService.list(me.id(), status, orderId, page, size)));
  }

  @PatchMapping("/{id}/confirm")
  public ResponseEntity<ApiResponse<OrderItemResponse>> confirm(
      @AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.ok(sellerOrderService.confirm(me.id(), id)));
  }

  // Body tuỳ chọn: không gửi body thì huỷ không có lý do.
  @PatchMapping("/{id}/cancel")
  public ResponseEntity<ApiResponse<OrderItemResponse>> cancel(
      @AuthenticationPrincipal AuthUser me,
      @PathVariable UUID id,
      @Valid @RequestBody(required = false) OrderItemCancelRequest request) {
    String reason = request == null ? null : request.cancelReason();
    return ResponseEntity.ok(ApiResponse.ok(sellerOrderService.cancel(me.id(), id, reason)));
  }
}
