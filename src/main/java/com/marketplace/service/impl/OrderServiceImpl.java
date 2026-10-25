package com.marketplace.service.impl;

import com.marketplace.common.exception.BusinessException;
import com.marketplace.common.response.PageResponse;
import com.marketplace.common.util.Pages;
import com.marketplace.common.util.TimeProvider;
import com.marketplace.dto.request.CheckoutRequest;
import com.marketplace.dto.response.OrderResponse;
import com.marketplace.dto.response.OrderSummaryResponse;
import com.marketplace.entity.CartItem;
import com.marketplace.entity.Order;
import com.marketplace.entity.OrderItem;
import com.marketplace.entity.Product;
import com.marketplace.entity.Shipment;
import com.marketplace.entity.ShipmentOrderItem;
import com.marketplace.entity.User;
import com.marketplace.entity.Voucher;
import com.marketplace.enums.OrderItemStatus;
import com.marketplace.enums.OrderStatus;
import com.marketplace.enums.PaymentMethod;
import com.marketplace.enums.ShipmentStatus;
import com.marketplace.mapper.OrderMapper;
import com.marketplace.repository.CartItemRepository;
import com.marketplace.repository.OrderRepository;
import com.marketplace.repository.ProductRepository;
import com.marketplace.repository.UserRepository;
import com.marketplace.service.OrderService;
import com.marketplace.service.PaymentService;
import com.marketplace.service.ProductService;
import com.marketplace.service.VoucherService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
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
 * từng dòng hàng, trừ tồn kho có điều kiện, tạo payment chờ (COD chờ thu, hoặc lần thử VNPAY đầu tiên kèm
 * link thanh toán) và xoá các dòng đã mua khỏi giỏ.
 * Tất cả nằm trong MỘT transaction: lỗi ở bất kỳ bước nào thì hoàn tác hết (không order, kho và giỏ
 * giữ nguyên). Mã voucher (nếu có) được trừ lượt và áp giảm giá trong cùng transaction.
 *
 * <p>Service này cũng sở hữu luật tính tổng tiền và trạng thái của order; seller, shipment và job quá hạn
 * gọi lại các hàm ở đây thay vì tự đổi order.
 */
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

  /** Lý do ghi vào các dòng bị huỷ vì quá hạn thanh toán trước. */
  static final String EXPIRED_REASON = "Payment deadline expired";

  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

  private final UserRepository userRepository;
  private final CartItemRepository cartItemRepository;
  private final ProductRepository productRepository;
  private final OrderRepository orderRepository;
  private final PaymentService paymentService;
  private final ProductService productService;
  private final VoucherService voucherService;
  private final OrderMapper orderMapper;
  private final TimeProvider timeProvider;

  @Override
  @Transactional
  public OrderResponse checkout(UUID customerId, CheckoutRequest request, String clientIp) {
    // Bước 1: khoá dòng user (giống giỏ hàng) để hai lần checkout cùng một giỏ chạy lần lượt,
    // tránh cùng mua một dòng giỏ hai lần.
    User customer = userRepository.lockRequired(customerId);

    // Bước 2: kiểm tra yêu cầu. Id trùng là lỗi 400.
    Set<UUID> ids = new HashSet<>(request.cartItemIds());
    if (ids.size() != request.cartItemIds().size()) {
      throw BusinessException.validation("cartItemIds must not contain duplicates");
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
      productService.ensureActive(product);
      if (productRepository.decreaseStock(product.getId(), line.getQuantity()) != 1) {
        throw BusinessException.insufficientStock("Not enough stock for " + product.getName());
      }
    }

    // Bước 5: tạo order và các dòng hàng theo đúng thứ tự khách chọn. Mỗi dòng chụp tên, giá và seller.
    // Order mới ở PENDING, phí ship cố định 0.00.
    Order order =
        Order.builder()
            .customer(customer)
            .shippingAddress(request.shippingAddress().trim())
            .createdAt(timeProvider.now())
            .status(OrderStatus.PENDING)
            .shippingFee(ZERO)
            .discountAmount(ZERO)
            .totalAmount(ZERO)
            .build();
    Map<UUID, CartItem> byId =
        lines.stream().collect(Collectors.toMap(CartItem::getId, Function.identity()));
    for (UUID id : request.cartItemIds()) {
      CartItem line = byId.get(id);
      order.getItems().add(snapshot(order, line.getProduct(), line.getQuantity()));
    }
    // Có mã voucher: trừ một lượt (hết hạn, hết lượt hay không có thì lỗi và cả checkout hoàn tác) rồi áp
    // giảm giá, TRƯỚC khi tạo payment để COD và VNPAY nhận đúng tổng sau giảm.
    if (request.voucherCode() != null && !request.voucherCode().isBlank()) {
      order.setVoucher(voucherService.consume(request.voucherCode(), timeProvider.now()));
    }
    recalculateTotal(order);
    orderRepository.save(order); // lưu kèm các dòng hàng (cascade PERSIST)

    // Bước 6: lần thanh toán đầu tiên, số tiền bằng tổng order. COD: chờ thu tiền khi giao. VNPAY: lần thử
    // chờ khách trả tiền, kèm link thanh toán có chữ ký (hạn 24 giờ tính từ lúc đặt).
    String paymentUrl = null;
    if (request.paymentMethod() == PaymentMethod.VNPAY) {
      paymentUrl = paymentService.openVnpayAttempt(order, clientIp);
    } else {
      paymentService.openCod(order);
    }

    // Bước 7: xoá các dòng đã mua; các dòng không chọn vẫn ở lại giỏ.
    cartItemRepository.deleteAll(lines);
    return orderMapper.toResponse(order, paymentUrl);
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
        .map(order -> orderMapper.toResponse(order, null))
        .orElseThrow(() -> BusinessException.notFound("Order", orderId));
  }

  @Override
  @Transactional
  public OrderResponse confirmReceived(UUID customerId, UUID orderId) {
    // Khoá order (cùng khoá với các luồng đổi trạng thái khác); của customer khác thì 404 như không tồn tại.
    Order order =
        orderRepository
            .findByIdForUpdate(orderId)
            .filter(o -> o.getCustomer().getId().equals(customerId))
            .orElseThrow(() -> BusinessException.notFound("Order", orderId));
    // Chỉ từ DELIVERED -> COMPLETED, trạng thái khác thì 400.
    if (order.getStatus() != OrderStatus.DELIVERED) {
      throw BusinessException.validation("Only a DELIVERED order can be confirmed as received");
    }
    order.setStatus(OrderStatus.COMPLETED);
    return orderMapper.toResponse(order, null);
  }

  @Override
  public void cancelItem(OrderItem item, String reason) {
    if (item.getStatus() == OrderItemStatus.CANCELLED) {
      throw BusinessException.validation("Order item is already cancelled");
    }
    item.setStatus(OrderItemStatus.CANCELLED);
    item.setCancelReason(reason == null || reason.isBlank() ? null : reason.trim());
  }

  @Override
  public void recalculateAfterCancellation(Order order) {
    if (order.getItems().stream().allMatch(OrderServiceImpl::isCancelled)) {
      order.setStatus(OrderStatus.CANCELLED);
      order.setDiscountAmount(ZERO);
      order.setTotalAmount(ZERO);
    } else {
      recalculateTotal(order);
    }
    paymentService.syncPendingPayments(order);
  }

  @Override
  public void completeIfAllDelivered(Order order, Instant now) {
    if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.PAID) {
      return;
    }
    // Id các dòng đã nằm trong một shipment DELIVERED.
    Set<UUID> delivered =
        order.getShipments().stream()
            .filter(shipment -> shipment.getStatus() == ShipmentStatus.DELIVERED)
            .map(Shipment::getShipmentItems)
            .flatMap(List::stream)
            .map(ShipmentOrderItem::getOrderItem)
            .map(OrderItem::getId)
            .collect(Collectors.toSet());
    boolean allDelivered =
        order.getItems().stream()
            .filter(item -> !isCancelled(item))
            .allMatch(item -> delivered.contains(item.getId()));
    if (allDelivered) {
      order.setStatus(OrderStatus.DELIVERED);
      paymentService.completeCod(order, now);
    }
  }

  @Override
  @Transactional
  public boolean cancelExpiredOrder(UUID orderId, Instant now) {
    // Khoá order rồi kiểm lại: có thể khách vừa trả tiền xong sau lúc job lấy danh sách.
    Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
    if (order == null
        || order.getStatus() != OrderStatus.PENDING
        || !paymentService.isPrepayment(order)
        || paymentService.hasCompletedPayment(order)
        || !paymentService.isPastDeadline(order, now)) {
      return false;
    }
    // Huỷ các dòng còn lại (dòng đã huỷ từ trước không bị cộng kho lần hai), rồi trả kho theo thứ tự id
    // product (giống checkout) để không khoá chéo với các giao dịch khác.
    List<OrderItem> remaining = order.getItems().stream().filter(item -> !isCancelled(item)).toList();
    remaining.forEach(item -> cancelItem(item, EXPIRED_REASON));
    remaining.stream()
        .sorted(Comparator.comparing(item -> item.getProduct().getId()))
        .forEach(item -> productRepository.increaseStock(item.getProduct().getId(), item.getQuantity()));
    // Không còn dòng nào: order CANCELLED, tổng 0.00, payment đang chờ FAILED.
    recalculateAfterCancellation(order);
    return true;
  }

  /**
   * Chụp lại product tại thời điểm checkout: tên và giá được copy vào dòng hàng, nên seller đổi giá hay
   * đổi tên sau này không ảnh hưởng đơn cũ. Dòng gắn với seller của product (đơn nhiều seller).
   */
  private static OrderItem snapshot(Order order, Product product, int quantity) {
    return OrderItem.builder()
        .order(order)
        .product(product)
        .seller(product.getSeller())
        .productName(product.getName())
        .unitPrice(product.getPrice())
        .quantity(quantity)
        .status(OrderItemStatus.PENDING)
        .build();
  }

  /**
   * Tạm tính = cộng (giá x số lượng) các dòng chưa huỷ. Giảm giá = % của voucher trên tạm tính, làm tròn 2
   * chữ số; tính lại mỗi lần để huỷ bớt dòng không làm tổng âm. Tổng = tạm tính - giảm giá + phí ship.
   */
  private static void recalculateTotal(Order order) {
    BigDecimal subtotal =
        order.getItems().stream()
            .filter(item -> !isCancelled(item))
            .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    Voucher voucher = order.getVoucher();
    BigDecimal discount =
        voucher == null
            ? ZERO
            : subtotal
                .multiply(BigDecimal.valueOf(voucher.getDiscountPercent()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    order.setDiscountAmount(discount);
    order.setTotalAmount(subtotal.subtract(discount).add(order.getShippingFee()));
  }

  private static boolean isCancelled(OrderItem item) {
    return item.getStatus() == OrderItemStatus.CANCELLED;
  }
}
