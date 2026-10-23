package com.marketplace.dto.request;

import com.marketplace.enums.DeliveryMethod;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Body của {@code POST /seller/shipments}.
 *
 * @param orderItemIds các dòng đã CONFIRMED của chính seller, cùng {@code orderId}
 * @param carrierId bắt buộc khi {@code PLATFORM_DELIVERY}, phải trống khi {@code SELF_DELIVERY} (service kiểm)
 */
public record ShipmentCreateRequest(
    @NotNull UUID orderId,
    @NotNull DeliveryMethod deliveryMethod,
    UUID carrierId,
    @NotEmpty List<@NotNull UUID> orderItemIds) {}
