package com.marketplace.controller;

import com.marketplace.common.response.ApiResponse;
import com.marketplace.dto.request.CartItemAddRequest;
import com.marketplace.dto.request.CartItemUpdateRequest;
import com.marketplace.dto.response.CartResponse;
import com.marketplace.security.AuthUser;
import com.marketplace.service.CartService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Giỏ hàng của customer. URL thật là {@code /api/v1/cart/...}; chỉ role CUSTOMER (luật
 * {@code /api/v1/cart/**} ở SecurityConfig). Chủ giỏ lấy từ token qua {@code me}, không nhận từ client.
 * Mọi thao tác thành công đều trả 200 kèm cả giỏ.
 */
@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

  private final CartService cartService;

  @GetMapping
  public ResponseEntity<ApiResponse<CartResponse>> getCart(@AuthenticationPrincipal AuthUser me) {
    return ResponseEntity.ok(ApiResponse.ok(cartService.getCart(me.id())));
  }

  @PostMapping("/items")
  public ResponseEntity<ApiResponse<CartResponse>> addItem(
      @AuthenticationPrincipal AuthUser me, @Valid @RequestBody CartItemAddRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(cartService.addItem(me.id(), request)));
  }

  @PatchMapping("/items/{id}")
  public ResponseEntity<ApiResponse<CartResponse>> updateItem(
      @AuthenticationPrincipal AuthUser me,
      @PathVariable UUID id,
      @Valid @RequestBody CartItemUpdateRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(cartService.updateItem(me.id(), id, request)));
  }

  @DeleteMapping("/items/{id}")
  public ResponseEntity<ApiResponse<CartResponse>> removeItem(
      @AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.ok(cartService.removeItem(me.id(), id)));
  }
}
