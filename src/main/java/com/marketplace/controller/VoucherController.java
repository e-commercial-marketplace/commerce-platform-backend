package com.marketplace.controller;

import com.marketplace.common.response.ApiResponse;
import com.marketplace.common.response.PageResponse;
import com.marketplace.dto.response.VoucherResponse;
import com.marketplace.dto.response.VoucherSummaryResponse;
import com.marketplace.service.VoucherService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Khách kiểm tra mã voucher trước khi checkout. URL thật là {@code /api/v1/vouchers/...}; chỉ role
 * CUSTOMER (luật CUSTOMER_ONLY). Không trừ lượt dùng: lượt chỉ bị trừ khi checkout.
 */
@Validated
@RestController
@RequestMapping("/vouchers")
@RequiredArgsConstructor
public class VoucherController {

  private final VoucherService voucherService;

  /** Công khai (PUBLIC_GET): mọi người, kể cả Guest, xem được voucher đang dùng được. */
  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<VoucherSummaryResponse>>> list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return ResponseEntity.ok(ApiResponse.ok(voucherService.listUsableVouchers(page, size)));
  }

  @GetMapping("/{code}/validate")
  public ResponseEntity<ApiResponse<VoucherResponse>> validate(@PathVariable String code) {
    return ResponseEntity.ok(ApiResponse.ok(voucherService.checkVoucherUsable(code)));
  }
}
