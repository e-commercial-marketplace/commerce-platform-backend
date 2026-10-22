package com.marketplace.service;

import com.marketplace.common.response.PageResponse;
import com.marketplace.dto.request.CheckoutRequest;
import com.marketplace.dto.response.OrderResponse;
import com.marketplace.dto.response.OrderSummaryResponse;
import java.util.UUID;

/** Checkout và tra cứu order của customer đang đăng nhập. */
public interface OrderService {

  /** Tạo một order từ các dòng giỏ được chọn, trong một transaction duy nhất. */
  OrderResponse checkout(UUID customerId, CheckoutRequest request);

  /** Order của customer, mới nhất trước. */
  PageResponse<OrderSummaryResponse> listOrders(UUID customerId, int page, int size);

  /** Một order của customer; không có hoặc của người khác thì 404. */
  OrderResponse getOrder(UUID customerId, UUID orderId);
}
