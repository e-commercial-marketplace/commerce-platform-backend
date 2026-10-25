package com.marketplace.dto.response;

import java.time.Instant;

/** Một voucher của nền tảng (admin xem, và trả về khi khách kiểm tra mã). */
public record VoucherResponse(
    String code,
    String description,
    int discountPercent,
    Instant validFrom,
    Instant validTo,
    int usageLimit,
    int timesUsed) {}
