package com.marketplace.controller;

import com.marketplace.common.response.ApiResponse;
import com.marketplace.dto.response.VoucherResponse;
import com.marketplace.service.VoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Khách kiểm tra mã voucher trước khi checkout. URL thật là {@code /api/v1/vouchers/...}; chỉ role
 * CUSTOMER (luật CUSTOMER_ONLY). Không trừ lượt dùng: lượt chỉ bị trừ khi checkout.
 */
@RestController
@RequestMapping("/vouchers")
@RequiredArgsConstructor
public class VoucherController {

  private final VoucherService voucherService;

  @GetMapping("/{code}/validate")
  public ResponseEntity<ApiResponse<VoucherResponse>> validate(@PathVariable String code) {
    return ResponseEntity.ok(ApiResponse.ok(voucherService.checkVoucherUsable(code)));
  }
}
