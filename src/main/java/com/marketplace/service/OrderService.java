package com.marketplace.service;

import com.marketplace.common.response.PageResponse;
import com.marketplace.dto.request.CheckoutRequest;
import com.marketplace.dto.response.OrderResponse;
import com.marketplace.dto.response.OrderSummaryResponse;
import java.time.Duration;
import java.util.UUID;

/** Checkout và tra cứu order của customer đang đăng nhập. */
public interface OrderService {

  /** Order DELIVERED tự chuyển COMPLETED nếu khách không xác nhận sau khoảng này (FR-ORD-13). */
  Duration AUTO_COMPLETE_AFTER = Duration.ofDays(3);

  /**
   * Tạo một order từ các dòng giỏ được chọn, trong một transaction duy nhất.
   *
   * @param clientIp IP của khách, gửi kèm cho VNPAY khi thanh toán qua VNPAY
   */
  OrderResponse checkout(UUID customerId, CheckoutRequest request, String clientIp);

  /** Order của customer, mới nhất trước. */
  PageResponse<OrderSummaryResponse> listOrders(UUID customerId, int page, int size);

  /** Một order của customer; không có hoặc của người khác thì 404. */
  OrderResponse getOrder(UUID customerId, UUID orderId);

  /** Khách xác nhận đã nhận hàng (DELIVERED -> COMPLETED); không có hoặc của người khác thì 404. */
  OrderResponse confirmReceived(UUID customerId, UUID orderId);
}
