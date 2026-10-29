package com.marketplace.controller;

import com.marketplace.common.response.ApiResponse;
import com.marketplace.common.response.PageResponse;
import com.marketplace.common.util.ClientIp;
import com.marketplace.dto.request.CheckoutRequest;
import com.marketplace.dto.response.OrderResponse;
import com.marketplace.dto.response.OrderSummaryResponse;
import com.marketplace.security.AuthUser;
import com.marketplace.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Checkout và order của customer. URL thật là {@code /api/v1/orders/...}; chỉ role CUSTOMER (luật
 * {@code /api/v1/orders/**} ở SecurityConfig). Customer lấy từ token qua {@code me}.
 */
@Validated
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

  private final OrderService orderService;

  // 201 Created: checkout tạo ra một order mới. IP của khách cần cho VNPAY (vnp_IpAddr).
  @PostMapping
  public ResponseEntity<ApiResponse<OrderResponse>> checkout(
      @AuthenticationPrincipal AuthUser me,
      @Valid @RequestBody CheckoutRequest request,
      HttpServletRequest http) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(orderService.checkout(me.id(), request, ClientIp.of(http))));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<OrderSummaryResponse>>> list(
      @AuthenticationPrincipal AuthUser me,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return ResponseEntity.ok(ApiResponse.ok(orderService.listMyOrders(me.id(), page, size)));
  }

  // Khách xác nhận đã nhận hàng, đóng order (DELIVERED -> COMPLETED).
  @PatchMapping("/{id}/received")
  public ResponseEntity<ApiResponse<OrderResponse>> confirmReceived(
      @AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.ok(orderService.confirmOrderReceived(me.id(), id)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<OrderResponse>> get(
      @AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.ok(orderService.getMyOrder(me.id(), id)));
  }
}
