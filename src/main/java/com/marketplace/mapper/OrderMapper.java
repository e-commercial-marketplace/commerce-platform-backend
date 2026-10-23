package com.marketplace.mapper;

import com.marketplace.dto.response.OrderItemResponse;
import com.marketplace.dto.response.OrderResponse;
import com.marketplace.dto.response.OrderSummaryResponse;
import com.marketplace.entity.Order;
import com.marketplace.entity.OrderItem;
import com.marketplace.entity.Payment;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Đổi order sang response. Trạng thái và phương thức thanh toán lấy từ lần thanh toán hiện tại của
 * order (xem {@link #currentPayment}).
 */
@Mapper(componentModel = "spring", uses = ShipmentMapper.class)
public interface OrderMapper {

  // paymentUrl chỉ có ở response checkout VNPAY; xem order thì truyền null.
  @Mapping(target = "paymentStatus", expression = "java(currentPayment(order).getStatus())")
  @Mapping(target = "paymentMethod", expression = "java(currentPayment(order).getMethod())")
  @Mapping(target = "voucherCode", source = "order.voucher.code") // voucher null thì voucherCode null
  @Mapping(target = "paymentUrl", source = "paymentUrl")
  OrderResponse toResponse(Order order, String paymentUrl);

  @Mapping(target = "paymentStatus", expression = "java(currentPayment(order).getStatus())")
  @Mapping(target = "paymentMethod", expression = "java(currentPayment(order).getMethod())")
  OrderSummaryResponse toSummary(Order order);

  @Mapping(target = "orderId", source = "order.id")
  @Mapping(target = "productId", source = "product.id")
  @Mapping(target = "sellerId", source = "seller.id") // proxy LAZY: đọc id không cần query thêm
  OrderItemResponse toItem(OrderItem item);

  /**
   * Lần thanh toán đang có hiệu lực: lần đã hoàn tất nếu có, không thì lần đang chờ, không thì lần cuối.
   * Không dựa vào thứ tự danh sách vì DB không đảm bảo thứ tự khi đọc lên (bảng payments không có cột
   * thời gian). Checkout luôn tạo một payment nên danh sách không rỗng.
   */
  default Payment currentPayment(Order order) {
    List<Payment> payments = order.getPayments();
    return payments.stream()
        .filter(Payment::isCompleted)
        .findFirst()
        .or(() -> payments.stream().filter(Payment::isPending).findFirst())
        .orElseGet(() -> payments.get(payments.size() - 1));
  }
}
