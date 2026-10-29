package com.marketplace.service;

import com.marketplace.common.response.PageResponse;
import com.marketplace.dto.request.ShipmentCreateRequest;
import com.marketplace.dto.request.ShipmentStatusUpdateRequest;
import com.marketplace.dto.response.ShipmentResponse;
import com.marketplace.enums.ShipmentStatus;
import java.util.UUID;

/** Shipment của seller theo features/shipment/API_CONTRACT.md. */
public interface ShipmentService {

  /** Tạo shipment từ các dòng đã CONFIRMED của seller trong một order. */
  ShipmentResponse createShipment(UUID sellerId, ShipmentCreateRequest request);

  /** Shipment của seller, mới nhất trước, lọc tuỳ chọn theo trạng thái và order. */
  PageResponse<ShipmentResponse> listMyShipments(
      UUID sellerId, ShipmentStatus status, UUID orderId, int page, int size);

  /** Đổi trạng thái shipment, kèm trả kho, huỷ dòng hoặc hoàn tất order khi cần. */
  ShipmentResponse updateShipmentStatus(UUID sellerId, UUID shipmentId, ShipmentStatusUpdateRequest request);
}
