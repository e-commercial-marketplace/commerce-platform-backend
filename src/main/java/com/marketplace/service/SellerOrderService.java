package com.marketplace.service;

import com.marketplace.common.response.PageResponse;
import com.marketplace.dto.response.OrderItemResponse;
import com.marketplace.enums.OrderItemStatus;
import java.util.UUID;

/** Seller xem và xử lý các dòng hàng của chính mình; seller phải đang APPROVED. */
public interface SellerOrderService {

  /** Các dòng hàng của seller, order mới nhất trước; {@code status}, {@code orderId} có thể null. */
  PageResponse<OrderItemResponse> list(
      UUID sellerId, OrderItemStatus status, UUID orderId, int page, int size);

  /** PENDING sang CONFIRMED. */
  OrderItemResponse confirm(UUID sellerId, UUID orderItemId);

  /** Huỷ dòng hàng: trả kho, tính lại order và payment đang chờ; hết dòng thì huỷ cả order. */
  OrderItemResponse cancel(UUID sellerId, UUID orderItemId, String reason);
}
