package com.marketplace.dto.request;

import com.marketplace.common.validation.NullOrNotBlank;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Body của {@code PATCH /seller/products/{id}}: mọi trường đều tuỳ chọn, trường nào bỏ trống
 * ({@code null}) thì giữ nguyên giá trị cũ. Tồn kho không đổi ở đây mà đi qua endpoint {@code /stock}.
 *
 * <p>Các ràng buộc chỉ kiểm tra khi trường có giá trị (giá trị {@code null} luôn hợp lệ).
 */
public record ProductUpdateRequest(
    // Nếu có tên thì không được toàn khoảng trắng.
    @NullOrNotBlank @Size(max = 255) String name,
    String description,
    @Size(max = 2048) String imageUrl,
    @DecimalMin(value = "0", inclusive = false) @Digits(integer = 17, fraction = 2) BigDecimal price,
    UUID categoryId,
    Boolean active) {}
