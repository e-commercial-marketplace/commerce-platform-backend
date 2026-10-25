package com.marketplace.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Body của {@code POST /admin/vouchers}. {@code code} được bỏ khoảng trắng và viết hoa trước khi lưu.
 *
 * @param code chỉ chữ, số, {@code -}, {@code _}; tối đa 64 ký tự (khoảng trắng hai đầu được bỏ qua)
 * @param validFrom phải trước {@code validTo} (entity kiểm, vì cần cả hai giá trị)
 */
public record VoucherCreateRequest(
    @NotNull @Pattern(regexp = "\\s*[A-Za-z0-9_-]{1,64}\\s*") String code,
    @Size(max = 1000) String description,
    @NotNull @Min(1) @Max(100) Integer discountPercent,
    @NotNull Instant validFrom,
    @NotNull Instant validTo,
    @NotNull @Min(1) Integer usageLimit) {}
