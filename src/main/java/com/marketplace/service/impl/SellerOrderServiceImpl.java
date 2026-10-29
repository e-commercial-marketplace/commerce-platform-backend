package com.marketplace.service.impl;

import com.marketplace.common.exception.BusinessException;
import com.marketplace.common.response.PageResponse;
import com.marketplace.dto.response.OrderItemResponse;
import com.marketplace.entity.Order;
import com.marketplace.entity.OrderItem;
import com.marketplace.enums.OrderItemStatus;
import com.marketplace.enums.OrderStatus;
import com.marketplace.mapper.OrderMapper;
import com.marketplace.repository.OrderItemRepository;
import com.marketplace.repository.OrderRepository;
import com.marketplace.repository.ShipmentOrderItemRepository;
import com.marketplace.repository.UserRepository;
import com.marketplace.service.OrderService;
import com.marketplace.service.PaymentService;
import com.marketplace.service.ProductService;
import com.marketplace.service.SellerOrderService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Xử lý đơn phía seller theo features/order/API_CONTRACT.md. Seller chỉ thấy và chỉ sửa dòng hàng của
 * mình. Huỷ một dòng nằm trong MỘT transaction: trả kho, tính lại tổng order và số tiền payment đang chờ;
 * huỷ hết dòng thì order CANCELLED và payment đang chờ FAILED. Order đã thanh toán trước thì không huỷ
 * được.
 *
 * <p>Thứ tự khoá luôn là dòng hàng rồi tới order, nên các request chạy song song không khoá chéo nhau.
 */
@Service
@RequiredArgsConstructor
public class SellerOrderServiceImpl implements SellerOrderService {

  private final OrderItemRepository orderItemRepository;
  private final OrderRepository orderRepository;
  private final ShipmentOrderItemRepository shipmentOrderItemRepository;
  private final ProductService productService;
  private final UserRepository userRepository;
  private final OrderService orderService;
  private final PaymentService paymentService;
  private final OrderMapper orderMapper;

  @Override
  @Transactional(readOnly = true)
  public PageResponse<OrderItemResponse> list(
      UUID sellerId, OrderItemStatus status, UUID orderId, int page, int size) {
    userRepository.getApprovedSeller(sellerId);
    // Order mới nhất trước; id làm khoá phụ để thứ tự ổn định giữa các trang.
    PageRequest pageable =
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "order.createdAt").and(Sort.by("id")));
    return PageResponse.from(
        orderItemRepository.findForSeller(sellerId, status, orderId, pageable), orderMapper::toItem);
  }

  @Override
  @Transactional
  public OrderItemResponse confirm(UUID sellerId, UUID orderItemId) {
    userRepository.getApprovedSeller(sellerId);
    OrderItem item = lockOwnedItem(sellerId, orderItemId);
    // Chỉ đi được từ PENDING, ngược lại 400.
    if (item.getStatus() != OrderItemStatus.PENDING) {
      throw BusinessException.validation("Only a PENDING order item can be confirmed");
    }
    // Order trả trước qua VNPAY chỉ được xác nhận khi đã PAID (FR-FUL-05), để seller không chuẩn bị hàng
    // cho đơn khách chưa trả tiền.
    Order order = item.getOrder();
    if (paymentService.isPrepayment(order) && order.getStatus() != OrderStatus.PAID) {
      throw BusinessException.validation("The order is not paid yet");
    }
    item.setStatus(OrderItemStatus.CONFIRMED);
    return orderMapper.toItem(item);
  }

  @Override
  @Transactional
  public OrderItemResponse cancel(UUID sellerId, UUID orderItemId, String reason) {
    // Bước 1: seller đã duyệt, dòng hàng tồn tại (404) và là của seller này (403). Dòng được khoá.
    userRepository.getApprovedSeller(sellerId);
    OrderItem item = lockOwnedItem(sellerId, orderItemId);
    // Dòng đã gán vào shipment thì huỷ qua shipment (PATCH .../status CANCELLED), không huỷ lẻ từng dòng.
    if (shipmentOrderItemRepository.existsByOrderItemId(orderItemId)) {
      throw BusinessException.cannotCancelAfterShipment("Cancel the shipment instead");
    }
    // Bước 2: khoá order để hai seller huỷ hai dòng cùng order không tính tổng từ dữ liệu cũ.
    Order order =
        orderRepository
            .findByIdForUpdate(item.getOrder().getId())
            .orElseThrow(() -> BusinessException.notFound("Order", item.getOrder().getId()));
    // Bước 3: khách đã thanh toán trước thì không huỷ được, vì chưa có quy trình hoàn tiền.
    if (order.getStatus() == OrderStatus.PAID) {
      throw BusinessException.cannotCancelAfterPayment("The order is already paid");
    }
    // Bước 4: huỷ dòng (dòng đã huỷ thì 400, nên bước trả kho dưới đây chỉ chạy đúng một lần).
    orderService.cancelItem(item, reason);
    productService.increaseStock(item.getProduct().getId(), item.getQuantity());
    // Bước 5: tính lại tổng và đồng bộ payment đang chờ (COD nhận tổng mới, VNPAY thành FAILED); không
    // còn dòng nào thì order CANCELLED và mọi payment đang chờ FAILED.
    orderService.recalculateAfterCancellation(order);
    return orderMapper.toItem(item);
  }

  /** Khoá dòng hàng; không có thì 404, của seller khác thì 403 (giống quản lý sản phẩm của seller). */
  private OrderItem lockOwnedItem(UUID sellerId, UUID orderItemId) {
    OrderItem item =
        orderItemRepository
            .findByIdForUpdate(orderItemId)
            .orElseThrow(() -> BusinessException.notFound("Order item", orderItemId));
    if (!item.getSeller().getId().equals(sellerId)) {
      throw BusinessException.forbidden("Order item belongs to another seller");
    }
    return item;
  }
}
