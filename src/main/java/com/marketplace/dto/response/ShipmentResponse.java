package com.marketplace.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.marketplace.enums.DeliveryMethod;
import com.marketplace.enums.ShipmentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Một shipment (seller xem, và nằm trong chi tiết order của customer). Tiền là chuỗi thập phân VND.
 *
 * @param carrier đơn vị vận chuyển; {@code null} khi seller tự giao
 * @param items các dòng hàng của shipment; {@code quantity} là số lượng của cả dòng
 */
public record ShipmentResponse(
    UUID id,
    UUID orderId,
    UUID sellerId,
    DeliveryMethod deliveryMethod,
    ShipmentCarrierResponse carrier,
    ShipmentStatus status,
    String trackingCode,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal shippingFee,
    Instant shippedAt,
    Instant deliveredAt,
    List<ShipmentItemResponse> items) {

  /** Đơn vị vận chuyển gọn trong shipment: chỉ id và tên. */
  public record ShipmentCarrierResponse(UUID id, String name) {}

  /** Một dòng hàng trong shipment. */
  public record ShipmentItemResponse(UUID orderItemId, int quantity) {}
}
