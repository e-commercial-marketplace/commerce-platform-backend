package com.marketplace.service.impl;

import com.marketplace.common.exception.BusinessException;
import com.marketplace.common.exception.ResourceNotFoundException;
import com.marketplace.common.response.PageResponse;
import com.marketplace.common.util.Pages;
import com.marketplace.common.util.TimeProvider;
import com.marketplace.dto.request.CheckoutRequest;
import com.marketplace.dto.response.OrderResponse;
import com.marketplace.dto.response.OrderSummaryResponse;
import com.marketplace.entity.CartItem;
import com.marketplace.entity.Order;
import com.marketplace.entity.Payment;
import com.marketplace.entity.Product;
import com.marketplace.entity.User;
import com.marketplace.enums.PaymentMethod;
import com.marketplace.mapper.OrderMapper;
import com.marketplace.repository.CartItemRepository;
import com.marketplace.repository.OrderRepository;
import com.marketplace.repository.PaymentRepository;
import com.marketplace.repository.ProductRepository;
import com.marketplace.repository.UserRepository;
import com.marketplace.service.OrderService;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checkout theo features/order/API_CONTRACT.md: chỉ mua các dòng giỏ được chọn, chụp tên và giá vào
 * từng dòng hàng, trừ tồn kho có điều kiện, tạo payment COD chờ thu và xoá các dòng đã mua khỏi giỏ.
 * Tất cả nằm trong MỘT transaction: lỗi ở bất kỳ bước nào thì hoàn tác hết (không order, kho và giỏ
 * giữ nguyên). Voucher làm ở Phase 8 nên giảm giá luôn 0.00.
 */
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

  private final UserRepository userRepository;
  private final CartItemRepository cartItemRepository;
  private final ProductRepository productRepository;
  private final OrderRepository orderRepository;
  private final PaymentRepository paymentRepository;
  private final OrderMapper orderMapper;
  private final TimeProvider timeProvider;

  @Override
  @Transactional
  public OrderResponse checkout(UUID customerId, CheckoutRequest request) {
    // Bước 1: khoá dòng user (giống giỏ hàng) để hai lần checkout cùng một giỏ chạy lần lượt,
    // tránh cùng mua một dòng giỏ hai lần.
    User customer = userRepository.lockRequired(customerId);

    // Bước 2: kiểm tra yêu cầu. Id trùng và phương thức chưa hỗ trợ là lỗi 400.
    Set<UUID> ids = new HashSet<>(request.cartItemIds());
    if (ids.size() != request.cartItemIds().size()) {
      throw BusinessException.validation("cartItemIds must not contain duplicates");
    }
    if (request.paymentMethod() != PaymentMethod.COD) {
      throw BusinessException.validation("Only COD payment is supported for now");
    }

    // Bước 3: lấy các dòng được chọn, CHỈ trong giỏ của customer này. Thiếu dòng nào (không tồn tại hoặc
    // của người khác) thì 404, không phân biệt hai trường hợp để không lộ dữ liệu người khác.
    List<CartItem> lines = cartItemRepository.findByUserIdAndIdIn(customerId, ids);
    if (lines.size() != ids.size()) {
      throw BusinessException.cartItemNotFound("Cart item not found");
    }

    // Bước 4: trừ kho từng product. Sắp theo id product để mọi checkout khoá các dòng product theo cùng
    // một thứ tự, tránh deadlock khi hai người mua chéo nhau các món giống nhau.
    for (CartItem line : lines.stream().sorted(Comparator.comparing(l -> l.getProduct().getId())).toList()) {
      Product product = line.getProduct();
      product.ensureActive();
      if (productRepository.decreaseStock(product.getId(), line.getQuantity()) != 1) {
        throw BusinessException.insufficientStock("Not enough stock for " + product.getName());
      }
    }

    // Bước 5: tạo order và các dòng hàng theo đúng thứ tự khách chọn. Mỗi dòng chụp tên, giá và seller.
    Order order = Order.place(customer, request.shippingAddress().trim(), timeProvider.now());
    Map<UUID, CartItem> byId =
        lines.stream().collect(Collectors.toMap(CartItem::getId, Function.identity()));
    for (UUID id : request.cartItemIds()) {
      CartItem line = byId.get(id);
      order.addItem(line.getProduct(), line.getQuantity());
    }
    orderRepository.save(order); // lưu kèm các dòng hàng (cascade PERSIST)

    // Bước 6: một lần thanh toán COD chờ thu tiền khi giao, số tiền bằng tổng order.
    Payment payment = paymentRepository.save(Payment.pendingCod(order));
    order.getPayments().add(payment);

    // Bước 7: xoá các dòng đã mua; các dòng không chọn vẫn ở lại giỏ.
    cartItemRepository.deleteAll(lines);
    return orderMapper.toResponse(order);
  }

  @Override
  @Transactional(readOnly = true)
  public PageResponse<OrderSummaryResponse> listOrders(UUID customerId, int page, int size) {
    return PageResponse.from(
        orderRepository.findByCustomerId(customerId, Pages.newestFirst(page, size)),
        orderMapper::toSummary);
  }

  @Override
  @Transactional(readOnly = true)
  public OrderResponse getOrder(UUID customerId, UUID orderId) {
    return orderRepository
        .findByIdAndCustomerId(orderId, customerId)
        .map(orderMapper::toResponse)
        .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
  }
}
