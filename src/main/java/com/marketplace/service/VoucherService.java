package com.marketplace.service;

import com.marketplace.common.response.PageResponse;
import com.marketplace.dto.request.VoucherCreateRequest;
import com.marketplace.dto.request.VoucherUpdateRequest;
import com.marketplace.dto.response.VoucherResponse;
import com.marketplace.entity.Voucher;
import java.time.Instant;

/** Voucher của nền tảng theo features/voucher/API_CONTRACT.md. Mã không phân biệt hoa thường. */
public interface VoucherService {

  PageResponse<VoucherResponse> listVouchers(int page, int size);

  VoucherResponse createVoucher(VoucherCreateRequest request);

  VoucherResponse updateVoucher(String code, VoucherUpdateRequest request);

  /** Hết hạn ngay, không xoá (giữ lịch sử). */
  VoucherResponse expireVoucher(String code);

  /** Khách kiểm tra mã: dùng được thì trả voucher, không thì 404 hoặc 409 (không trừ lượt). */
  VoucherResponse checkVoucherUsable(String code);

  /**
   * Trừ một lượt dùng cho checkout (chạy trong transaction của checkout) và trả voucher; không có thì 404,
   * hết hạn hoặc hết lượt thì 409.
   */
  Voucher useVoucher(String code, Instant now);
}
