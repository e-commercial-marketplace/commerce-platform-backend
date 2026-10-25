package com.marketplace.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Body của {@code PATCH /admin/vouchers/{code}}. Trường {@code null} thì giữ nguyên; {@code code} không đổi
 * được. Kết quả vẫn phải có {@code validFrom < validTo} và {@code usageLimit >= timesUsed} (entity kiểm).
 */
public record VoucherUpdateRequest(
    @Size(max = 1000) String description,
    @Min(1) @Max(100) Integer discountPercent,
    Instant validFrom,
    Instant validTo,
    @Min(1) Integer usageLimit) {}
