package com.marketplace.mapper;

import com.marketplace.dto.response.OrderItemResponse;
import com.marketplace.dto.response.OrderResponse;
import com.marketplace.dto.response.OrderSummaryResponse;
import com.marketplace.entity.Order;
import com.marketplace.entity.OrderItem;
import com.marketplace.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Đổi order sang response. Trạng thái và phương thức thanh toán lấy từ lần thanh toán mới nhất của
 * order (Phase 4 mỗi order có đúng một payment COD).
 */
@Mapper(componentModel = "spring")
public interface OrderMapper {

  @Mapping(target = "paymentStatus", expression = "java(latestPayment(order).getStatus())")
  @Mapping(target = "paymentMethod", expression = "java(latestPayment(order).getMethod())")
  @Mapping(target = "voucherCode", source = "voucher.code") // voucher null thì voucherCode null
  OrderResponse toResponse(Order order);

  @Mapping(target = "paymentStatus", expression = "java(latestPayment(order).getStatus())")
  @Mapping(target = "paymentMethod", expression = "java(latestPayment(order).getMethod())")
  OrderSummaryResponse toSummary(Order order);

  @Mapping(target = "orderId", source = "order.id")
  @Mapping(target = "productId", source = "product.id")
  @Mapping(target = "sellerId", source = "seller.id") // proxy LAZY: đọc id không cần query thêm
  OrderItemResponse toItem(OrderItem item);

  /** Lần thanh toán được thêm sau cùng. Checkout luôn tạo một payment nên danh sách không rỗng. */
  default Payment latestPayment(Order order) {
    return order.getPayments().get(order.getPayments().size() - 1);
  }
}
