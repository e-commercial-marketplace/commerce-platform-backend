package com.marketplace.service.impl;

import com.marketplace.common.exception.BusinessException;
import com.marketplace.common.response.PageResponse;
import com.marketplace.dto.response.OrderItemResponse;
import com.marketplace.entity.Order;
import com.marketplace.entity.OrderItem;
import com.marketplace.entity.Payment;
import com.marketplace.enums.OrderItemStatus;
import com.marketplace.mapper.OrderMapper;
import com.marketplace.repository.OrderItemRepository;
import com.marketplace.repository.OrderRepository;
import com.marketplace.repository.ProductRepository;
import com.marketplace.repository.UserRepository;
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
 * huỷ hết dòng thì order CANCELLED và payment COD FAILED.
 *
 * <p>Thứ tự khoá luôn là dòng hàng rồi tới order, nên các request chạy song song không khoá chéo nhau.
 */
@Service
@RequiredArgsConstructor
public class SellerOrderServiceImpl implements SellerOrderService {

  private final OrderItemRepository orderItemRepository;
  private final OrderRepository orderRepository;
  private final ProductRepository productRepository;
  private final UserRepository userRepository;
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
    item.confirm(); // chỉ từ PENDING, ngược lại 400
    return orderMapper.toItem(item);
  }

  @Override
  @Transactional
  public OrderItemResponse cancel(UUID sellerId, UUID orderItemId, String reason) {
    // Bước 1: seller đã duyệt, dòng hàng tồn tại (404) và là của seller này (403). Dòng được khoá.
    userRepository.getApprovedSeller(sellerId);
    OrderItem item = lockOwnedItem(sellerId, orderItemId);
    // Bước 2: khoá order để hai seller huỷ hai dòng cùng order không tính tổng từ dữ liệu cũ.
    Order order =
        orderRepository
            .findByIdForUpdate(item.getOrder().getId())
            .orElseThrow(() -> BusinessException.notFound("Order", item.getOrder().getId()));
    // Bước 3: huỷ dòng (dòng đã huỷ thì 400, nên bước trả kho dưới đây chỉ chạy đúng một lần).
    item.cancel(reason);
    productRepository.increaseStock(item.getProduct().getId(), item.getQuantity());
    // Bước 4: tính lại tổng từ các dòng còn lại; không còn dòng nào thì order chuyển CANCELLED.
    order.recalculateAfterCancellation();
    // Bước 5: payment đang chờ nhận số tiền mới; order đã huỷ thì payment không còn thu được nên FAILED.
    for (Payment payment : order.getPayments()) {
      payment.updatePendingAmount(order.getTotalAmount());
      if (order.isCancelled()) {
        payment.markFailed();
      }
    }
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
