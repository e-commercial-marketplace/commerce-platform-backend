package com.marketplace.service;

import com.marketplace.common.response.PageResponse;
import com.marketplace.dto.request.CheckoutRequest;
import com.marketplace.dto.response.OrderResponse;
import com.marketplace.dto.response.OrderSummaryResponse;
import com.marketplace.entity.Order;
import com.marketplace.entity.OrderItem;
import java.time.Duration;
import java.time.Instant;
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

  /**
   * Huỷ một dòng hàng (đang PENDING hoặc CONFIRMED). Dòng đã huỷ thì 400, nhờ vậy kho không bị cộng trả
   * hai lần. Lý do trống được lưu là {@code null}. Người gọi tự trả kho rồi gọi
   * {@link #recalculateAfterCancellation}.
   */
  void cancelItem(OrderItem item, String reason);

  /**
   * Gọi sau khi huỷ dòng: tính lại tổng từ các dòng chưa huỷ (bản chụp của dòng đã huỷ giữ nguyên để xem
   * lịch sử). Không còn dòng nào thì huỷ cả order và tổng về 0.00. Sau đó đồng bộ các payment đang chờ.
   */
  void recalculateAfterCancellation(Order order);

  /**
   * Gọi sau khi một shipment chuyển DELIVERED. Mọi dòng chưa huỷ đều đã nằm trong shipment DELIVERED thì
   * order thành DELIVERED và payment COD đang chờ được ghi nhận đã thu (FR-SHIP-07, FR-PAY-02). Còn dòng
   * chưa giao, hoặc có shipment FAILED/RETURNED, thì order giữ nguyên (FR-SHIP-10).
   */
  void completeIfAllDelivered(Order order, Instant now);

  /**
   * Huỷ order trả trước đã quá hạn 24 giờ: huỷ các dòng còn lại, trả kho đúng một lần, payment đang chờ
   * thành FAILED. Kiểm lại điều kiện sau khi khoá order; không còn quá hạn thì không làm gì.
   *
   * @return {@code true} nếu order vừa bị huỷ
   */
  boolean cancelExpiredOrder(UUID orderId, Instant now);
}
