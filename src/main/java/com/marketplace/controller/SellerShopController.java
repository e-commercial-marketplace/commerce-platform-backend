package com.marketplace.controller;

import com.marketplace.common.response.ApiResponse;
import com.marketplace.dto.response.SellerShopResponse;
import com.marketplace.service.SellerShopService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trang shop công khai của seller (không cần token). URL thật là {@code /api/v1/sellers/{id}}; các
 * product của shop lấy bằng {@code GET /api/v1/products?sellerId={id}}.
 */
@RestController
@RequestMapping("/sellers")
@RequiredArgsConstructor
public class SellerShopController {

  private final SellerShopService sellerShopService;

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<SellerShopResponse>> getShop(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.ok(sellerShopService.getShop(id)));
  }
}
