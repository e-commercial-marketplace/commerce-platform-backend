package com.marketplace.service.impl;

import com.marketplace.common.exception.BusinessException;
import com.marketplace.common.response.PageResponse;
import com.marketplace.common.util.TimeProvider;
import com.marketplace.dto.request.ShipmentCreateRequest;
import com.marketplace.dto.request.ShipmentStatusUpdateRequest;
import com.marketplace.dto.response.ShipmentResponse;
import com.marketplace.entity.Carrier;
import com.marketplace.entity.Order;
import com.marketplace.entity.OrderItem;
import com.marketplace.entity.Shipment;
import com.marketplace.entity.ShipmentOrderItem;
import com.marketplace.entity.User;
import com.marketplace.enums.DeliveryMethod;
import com.marketplace.enums.OrderItemStatus;
import com.marketplace.enums.OrderStatus;
import com.marketplace.enums.ShipmentStatus;
import com.marketplace.mapper.ShipmentMapper;
import com.marketplace.repository.CarrierRepository;
import com.marketplace.repository.OrderItemRepository;
import com.marketplace.repository.OrderRepository;
import com.marketplace.repository.ShipmentOrderItemRepository;
import com.marketplace.repository.ShipmentRepository;
import com.marketplace.repository.UserRepository;
import com.marketplace.service.OrderService;
import com.marketplace.service.ProductService;
import com.marketplace.service.ShipmentService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shipment của seller theo features/shipment/API_CONTRACT.md.
 *
 * <p>Thứ tự khoá luôn là shipment, rồi các dòng hàng (theo id), rồi order. Seller huỷ dòng khoá dòng rồi
 * tới order, nên hai luồng không khoá chéo nhau.
 */
@Service
@RequiredArgsConstructor
public class ShipmentServiceImpl implements ShipmentService {

  /** Lý do ghi vào các dòng bị huỷ theo shipment. */
  static final String SHIPMENT_CANCELLED_REASON = "Shipment cancelled";

  /** Các bước được phép đi tiếp từ mỗi trạng thái (theo contract shipment); trạng thái khác là điểm cuối. */
  private static final Map<ShipmentStatus, Set<ShipmentStatus>> NEXT = new EnumMap<>(ShipmentStatus.class);

  static {
    NEXT.put(ShipmentStatus.PENDING, Set.of(ShipmentStatus.PREPARING, ShipmentStatus.CANCELLED));
    NEXT.put(ShipmentStatus.PREPARING, Set.of(ShipmentStatus.SHIPPED, ShipmentStatus.CANCELLED));
    NEXT.put(ShipmentStatus.SHIPPED, Set.of(ShipmentStatus.DELIVERED, ShipmentStatus.FAILED));
    NEXT.put(ShipmentStatus.FAILED, Set.of(ShipmentStatus.RETURNED));
  }

  private final ShipmentRepository shipmentRepository;
  private final ShipmentOrderItemRepository shipmentOrderItemRepository;
  private final OrderItemRepository orderItemRepository;
  private final OrderRepository orderRepository;
  private final CarrierRepository carrierRepository;
  private final ProductService productService;
  private final UserRepository userRepository;
  private final OrderService orderService;
  private final ShipmentMapper shipmentMapper;
  private final TimeProvider timeProvider;

  @Override
  @Transactional
  public ShipmentResponse createShipment(UUID sellerId, ShipmentCreateRequest request) {
    // Bước 1: seller đã duyệt; order phải tồn tại (404).
    User seller = userRepository.getApprovedSeller(sellerId);
    Order order =
        orderRepository
            .findById(request.orderId())
            .orElseThrow(() -> BusinessException.notFound("Order", request.orderId()));
    if (new HashSet<>(request.orderItemIds()).size() != request.orderItemIds().size()) {
      throw BusinessException.validation("orderItemIds must not contain duplicates");
    }
    // Bước 2: khoá từng dòng theo thứ tự id, để seller huỷ dòng cùng lúc phải chờ (không gán dòng đã huỷ).
    List<OrderItem> items =
        request.orderItemIds().stream().sorted().map(this::lockItem).toList();
    // Bước 3: dòng của seller khác thì 403; khác order, chưa CONFIRMED hoặc đã có shipment thì 400.
    for (OrderItem item : items) {
      if (!item.getSeller().getId().equals(sellerId)) {
        throw BusinessException.forbidden("Order item belongs to another seller");
      }
      if (!item.getOrder().getId().equals(order.getId())) {
        throw BusinessException.validation("All order items must belong to the order");
      }
      if (item.getStatus() != OrderItemStatus.CONFIRMED) {
        throw BusinessException.validation("Only CONFIRMED order items can be shipped");
      }
      if (shipmentOrderItemRepository.existsByOrderItemId(item.getId())) {
        throw BusinessException.validation("Order item is already in a shipment");
      }
    }
    // Bước 4: mỗi seller chỉ một shipment cho mỗi order; đơn vị vận chuyển đúng với cách giao.
    if (shipmentRepository.existsByOrderIdAndSellerId(order.getId(), sellerId)) {
      throw alreadyShipped();
    }
    Carrier carrier = resolveCarrier(request.deliveryMethod(), request.carrierId());
    try {
      // Flush ngay: hai request tạo shipment cho cùng order với các dòng khác nhau thì bên thua bật lỗi
      // unique (order, seller) tại đây.
      Shipment shipment =
          shipmentRepository.saveAndFlush(
              newShipment(order, seller, request.deliveryMethod(), carrier, items));
      return shipmentMapper.toResponse(shipment);
    } catch (DataIntegrityViolationException e) {
      throw alreadyShipped();
    }
  }

  @Override
  @Transactional(readOnly = true)
  public PageResponse<ShipmentResponse> listMyShipments(
      UUID sellerId, ShipmentStatus status, UUID orderId, int page, int size) {
    userRepository.getApprovedSeller(sellerId);
    // Order mới nhất trước; id làm khoá phụ để thứ tự ổn định giữa các trang (giống danh sách dòng hàng).
    PageRequest pageable =
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "order.createdAt").and(Sort.by("id")));
    return PageResponse.from(
        shipmentRepository.findForSeller(sellerId, status, orderId, pageable),
        shipmentMapper::toResponse);
  }

  @Override
  @Transactional
  public ShipmentResponse updateShipmentStatus(
      UUID sellerId, UUID shipmentId, ShipmentStatusUpdateRequest request) {
    // Bước 1: seller đã duyệt; khoá shipment (404), của seller khác thì 403.
    userRepository.getApprovedSeller(sellerId);
    Shipment shipment =
        shipmentRepository
            .findByIdForUpdate(shipmentId)
            .orElseThrow(() -> BusinessException.notFound("Shipment", shipmentId));
    if (!shipment.getSeller().getId().equals(sellerId)) {
      throw BusinessException.forbidden("Shipment belongs to another seller");
    }
    // Bước 2: khoá các dòng (theo id) rồi tới order, cùng thứ tự với seller huỷ dòng.
    List<OrderItem> items =
        shipment.getShipmentItems().stream()
            .map(mapping -> mapping.getOrderItem().getId())
            .sorted()
            .map(this::lockItem)
            .toList();
    Order order = orderRepository.findByIdForUpdate(shipment.getOrder().getId()).orElseThrow();
    ShipmentStatus target = request.status();
    if (target == ShipmentStatus.CANCELLED && order.getStatus() == OrderStatus.PAID) {
      // Khách đã trả trước, chưa có quy trình hoàn tiền.
      throw BusinessException.validation("A shipment of a paid order cannot be cancelled");
    }
    // Bước 3: đổi trạng thái (sai bước hoặc thiếu mã vận đơn thì 400).
    Instant now = timeProvider.now();
    moveTo(shipment, target, request.trackingCode(), now);
    // Bước 4: việc đi kèm theo trạng thái mới.
    switch (target) {
      // Mọi dòng chưa huỷ đã giao xong thì order DELIVERED và COD được ghi nhận đã thu.
      case DELIVERED -> orderService.markDeliveredIfAllShipped(order, now);
      // Hàng đã về kho seller: trả kho. Chỉ đi được từ FAILED nên chạy đúng một lần.
      case RETURNED -> items.forEach(this::restoreStock);
      case CANCELLED -> {
        // Chưa gửi đi: huỷ các dòng, trả kho, tính lại tổng và payment đang chờ (dùng lại từ Phase 6).
        items.forEach(item -> orderService.cancelOrderItem(item, SHIPMENT_CANCELLED_REASON));
        items.forEach(this::restoreStock);
        orderService.recalculateTotalsAfterCancel(order);
      }
      default -> {} // PREPARING, SHIPPED, FAILED: chỉ đổi trạng thái (FAILED không trả kho)
    }
    return shipmentMapper.toResponse(shipment);
  }

  /**
   * Tạo shipment PENDING gom các dòng hàng đã kiểm tra (cùng order, cùng seller, đã CONFIRMED). Phí ship
   * cố định 0.00 như checkout.
   */
  private static Shipment newShipment(
      Order order, User seller, DeliveryMethod method, Carrier carrier, List<OrderItem> items) {
    Shipment shipment =
        Shipment.builder()
            .order(order)
            .seller(seller)
            .deliveryMethod(method)
            .carrier(carrier)
            .status(ShipmentStatus.PENDING)
            .shippingFee(BigDecimal.ZERO.setScale(2))
            .build();
    // Gán nguyên từng dòng hàng; @MapsId tự điền khoá ghép từ shipment và dòng hàng lúc lưu.
    items.forEach(
        item ->
            shipment
                .getShipmentItems()
                .add(ShipmentOrderItem.builder().shipment(shipment).orderItem(item).build()));
    return shipment;
  }

  /**
   * Chuyển sang trạng thái mới nếu được phép, ngược lại 400. SHIPPED bắt buộc có mã vận đơn và ghi lúc
   * gửi; DELIVERED ghi lúc giao. Việc trả kho, huỷ dòng hay hoàn tất order làm sau bước này.
   */
  private static void moveTo(Shipment shipment, ShipmentStatus target, String trackingCode, Instant now) {
    ShipmentStatus current = shipment.getStatus();
    if (!NEXT.getOrDefault(current, Set.of()).contains(target)) {
      throw BusinessException.validation("Cannot move a shipment from " + current + " to " + target);
    }
    if (target == ShipmentStatus.SHIPPED) {
      if (trackingCode == null || trackingCode.isBlank()) {
        throw BusinessException.validation("trackingCode is required to mark a shipment SHIPPED");
      }
      shipment.setTrackingCode(trackingCode.trim());
      shipment.setShippedAt(now);
    }
    if (target == ShipmentStatus.DELIVERED) {
      shipment.setDeliveredAt(now);
    }
    shipment.setStatus(target);
  }

  /**
   * Đơn vị vận chuyển theo cách giao: giao qua nền tảng thì bắt buộc một đơn vị đang hoạt động; tự giao
   * thì không được có đơn vị.
   */
  private Carrier resolveCarrier(DeliveryMethod method, UUID carrierId) {
    if (method == DeliveryMethod.SELF_DELIVERY) {
      if (carrierId != null) {
        throw BusinessException.validation("carrierId must be empty for SELF_DELIVERY");
      }
      return null;
    }
    if (carrierId == null) {
      throw BusinessException.validation("carrierId is required for PLATFORM_DELIVERY");
    }
    return carrierRepository
        .findById(carrierId)
        .filter(Carrier::isActive)
        .orElseThrow(() -> BusinessException.validation("Carrier is unknown or inactive"));
  }

  private OrderItem lockItem(UUID orderItemId) {
    return orderItemRepository
        .findByIdForUpdate(orderItemId)
        .orElseThrow(() -> BusinessException.notFound("Order item", orderItemId));
  }

  private void restoreStock(OrderItem item) {
    productService.increaseStock(item.getProduct().getId(), item.getQuantity());
  }

  private static BusinessException alreadyShipped() {
    return BusinessException.validation("The seller already has a shipment for this order");
  }
}
