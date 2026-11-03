package com.marketplace.dto.response;

import java.time.Instant;

/** Voucher hiển thị công khai: không có số lượt dùng vì đó là dữ liệu nội bộ. */
public record VoucherSummaryResponse(
    String code, String description, int discountPercent, Instant validTo) {}
