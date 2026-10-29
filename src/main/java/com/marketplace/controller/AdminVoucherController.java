package com.marketplace.controller;

import com.marketplace.common.response.ApiResponse;
import com.marketplace.common.response.PageResponse;
import com.marketplace.dto.request.VoucherCreateRequest;
import com.marketplace.dto.request.VoucherUpdateRequest;
import com.marketplace.dto.response.VoucherResponse;
import com.marketplace.service.VoucherService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * Admin quản lý voucher. URL thật là {@code /api/v1/admin/vouchers/...}; role ADMIN được chặn ở
 * {@code SecurityConfig}. {@code DELETE} chỉ cho hết hạn (giữ lịch sử), nên trả 200 kèm voucher.
 */
@Validated
@RestController
@RequestMapping("/admin/vouchers")
@RequiredArgsConstructor
public class AdminVoucherController {

  private final VoucherService voucherService;

  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<VoucherResponse>>> list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return ResponseEntity.ok(ApiResponse.ok(voucherService.listVouchers(page, size)));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<VoucherResponse>> create(
      @Valid @RequestBody VoucherCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(voucherService.createVoucher(request)));
  }

  @PatchMapping("/{code}")
  public ResponseEntity<ApiResponse<VoucherResponse>> update(
      @PathVariable String code, @Valid @RequestBody VoucherUpdateRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(voucherService.updateVoucher(code, request)));
  }

  @DeleteMapping("/{code}")
  public ResponseEntity<ApiResponse<VoucherResponse>> expire(@PathVariable String code) {
    return ResponseEntity.ok(ApiResponse.ok(voucherService.expireVoucher(code)));
  }
}
