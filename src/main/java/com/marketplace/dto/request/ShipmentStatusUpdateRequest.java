package com.marketplace.dto.request;

import com.marketplace.enums.ShipmentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body của {@code PATCH /seller/shipments/{id}/status}.
 *
 * @param trackingCode mã vận đơn, bắt buộc khi chuyển sang SHIPPED (entity kiểm), bỏ qua với trạng thái khác
 */
public record ShipmentStatusUpdateRequest(
    @NotNull ShipmentStatus status, @Size(max = 255) String trackingCode) {}
