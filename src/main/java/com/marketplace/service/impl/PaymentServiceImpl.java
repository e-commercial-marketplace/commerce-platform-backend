package com.marketplace.service.impl;

import com.marketplace.common.exception.BusinessException;
import com.marketplace.common.util.TimeProvider;
import com.marketplace.dto.response.VnpayIpnResponse;
import com.marketplace.dto.response.VnpayPaymentResponse;
import com.marketplace.entity.Order;
import com.marketplace.entity.OrderItem;
import com.marketplace.entity.Payment;
import com.marketplace.repository.OrderRepository;
import com.marketplace.repository.PaymentRepository;
import com.marketplace.repository.ProductRepository;
import com.marketplace.service.PaymentService;
import com.marketplace.vnpay.VnpayGateway;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thanh toán VNPAY. Mọi thao tác đổi trạng thái đều KHOÁ order trước (cùng khoá với seller huỷ dòng),
 * rồi mới đọc payment, nên IPN, Return URL, retry, seller huỷ và job 24 giờ chạy lần lượt trên cùng một
 * order và luôn thấy dữ liệu mới nhất.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

  /** Lý do ghi vào các dòng bị huỷ vì quá hạn thanh toán trước. */
  static final String EXPIRED_REASON = "Payment deadline expired";

  // VNPAY báo thành công khi cả mã phản hồi và trạng thái giao dịch đều là "00".
  private static final String VNPAY_SUCCESS = "00";

  private final OrderRepository orderRepository;
  private final PaymentRepository paymentRepository;
  private final ProductRepository productRepository;
  private final VnpayGateway vnpayGateway;
  private final TimeProvider timeProvider;

  @Override
  public String openVnpayAttempt(Order order, String clientIp) {
    // Mã tham chiếu: UUID bỏ dấu gạch (32 ký tự chữ và số), duy nhất nhờ unique index trên transaction_id.
    String txnRef = UUID.randomUUID().toString().replace("-", "");
    Payment payment = paymentRepository.save(Payment.pendingVnpay(order, txnRef));
    order.getPayments().add(payment);
    Instant deadline = order.getCreatedAt().plus(PREPAYMENT_DEADLINE);
    return vnpayGateway.buildPaymentUrl(payment, clientIp, timeProvider.now(), deadline);
  }

  @Override
  @Transactional
  public VnpayPaymentResponse retryVnpay(UUID customerId, UUID orderId, String clientIp) {
    // Bước 1: khoá order; không có hoặc của customer khác thì 404 (không lộ order người khác có tồn tại).
    Order order =
        orderRepository
            .findByIdForUpdate(orderId)
            .filter(o -> o.getCustomer().getId().equals(customerId))
            .orElseThrow(() -> BusinessException.notFound("Order", orderId));
    // Bước 2: chỉ order VNPAY còn chờ, chưa trả và còn trong hạn 24 giờ mới thanh toán được.
    Instant now = timeProvider.now();
    if (!order.isPending() || !order.isPrepayment() || hasCompletedPayment(order)) {
      throw BusinessException.orderNotPayable("The order cannot be paid with VNPAY");
    }
    if (isPastDeadline(order, now)) {
      throw BusinessException.orderNotPayable("The payment deadline has passed");
    }
    // Bước 3: link cũ (nếu còn) hết dùng được, để mỗi lúc chỉ có một link thanh toán được cho order.
    order.getPayments().stream().filter(Payment::isVnpay).forEach(Payment::markFailed);
    // Bước 4: lần thử mới với tổng hiện tại và link mới.
    String paymentUrl = openVnpayAttempt(order, clientIp);
    Payment attempt = order.getPayments().get(order.getPayments().size() - 1);
    return new VnpayPaymentResponse(
        attempt.getId(), order.getId(), attempt.getAmount(), attempt.getStatus(), paymentUrl);
  }

  @Override
  @Transactional
  public VnpayResult handleVnpayResult(Map<String, String> params) {
    // Bước 1: chữ ký phải đúng, nếu không thì bỏ qua toàn bộ dữ liệu (có thể là request giả mạo).
    if (!vnpayGateway.verify(params)) {
      return new VnpayResult(VnpayIpnResponse.INVALID_SIGNATURE, null, null);
    }
    // Bước 2: tìm order theo mã tham chiếu rồi KHOÁ nó trước khi đọc payment.
    String txnRef = params.get("vnp_TxnRef");
    UUID orderId = paymentRepository.findOrderIdByTransactionId(txnRef).orElse(null);
    if (orderId == null) {
      return new VnpayResult(VnpayIpnResponse.NOT_FOUND, null, null);
    }
    Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow();
    Payment payment =
        order.getPayments().stream()
            .filter(p -> txnRef.equals(p.getTransactionId()))
            .findFirst()
            .orElseThrow();
    // Bước 3: số tiền VNPAY báo phải đúng số tiền của lần thử (VNPAY nhân 100).
    if (!amountMatches(params.get("vnp_Amount"), payment)) {
      return new VnpayResult(VnpayIpnResponse.INVALID_AMOUNT, orderId, null);
    }
    boolean success =
        VNPAY_SUCCESS.equals(params.get("vnp_ResponseCode"))
            && VNPAY_SUCCESS.equals(params.get("vnp_TransactionStatus"));
    // Bước 4: đã xử lý rồi (IPN và Return cùng tới, hoặc VNPAY gửi lại) thì không đổi gì thêm.
    if (!payment.isPending()) {
      if (success && !payment.isCompleted()) {
        // Khách đã trả tiền cho một lần thử đã bị huỷ (có retry, tổng đổi, hoặc order hết hạn): cần hoàn tay.
        log.warn(
            "VNPAY success for non-payable attempt txnRef={} order={}: refund manually",
            txnRef,
            orderId);
      }
      return new VnpayResult(VnpayIpnResponse.ALREADY_CONFIRMED, orderId, payment.getStatus());
    }
    // Bước 5: ghi nhận kết quả. Thành công: payment COMPLETED và order PAID; thất bại: chỉ payment FAILED,
    // order vẫn chờ để khách thanh toán lại.
    if (success) {
      payment.complete(timeProvider.now());
      order.markPaid();
    } else {
      payment.markFailed();
    }
    log.info("VNPAY result txnRef={} order={} -> {}", txnRef, orderId, payment.getStatus());
    return new VnpayResult(VnpayIpnResponse.CONFIRMED, orderId, payment.getStatus());
  }

  @Override
  @Transactional
  public boolean cancelExpiredOrder(UUID orderId, Instant now) {
    // Khoá order rồi kiểm lại: có thể khách vừa trả tiền xong sau lúc job lấy danh sách.
    Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
    if (order == null
        || !order.isPending()
        || !order.isPrepayment()
        || hasCompletedPayment(order)
        || !isPastDeadline(order, now)) {
      return false;
    }
    // Trả kho theo thứ tự id product (giống checkout) để không khoá chéo với các giao dịch khác.
    order.cancelRemainingItems(EXPIRED_REASON).stream()
        .sorted(Comparator.comparing(item -> item.getProduct().getId()))
        .forEach(this::restoreStock);
    // Không còn dòng nào: order CANCELLED, tổng 0.00, payment đang chờ FAILED.
    order.recalculateAfterCancellation();
    return true;
  }

  private void restoreStock(OrderItem item) {
    productRepository.increaseStock(item.getProduct().getId(), item.getQuantity());
  }

  private static boolean hasCompletedPayment(Order order) {
    return order.getPayments().stream().anyMatch(Payment::isCompleted);
  }

  private static boolean isPastDeadline(Order order, Instant now) {
    return !now.isBefore(order.getCreatedAt().plus(PREPAYMENT_DEADLINE));
  }

  /** {@code vnp_Amount} là số nguyên (tiền x 100); sai định dạng cũng coi như sai số tiền. */
  private static boolean amountMatches(String vnpAmount, Payment payment) {
    try {
      return Long.parseLong(vnpAmount) == VnpayGateway.toVnpayAmount(payment.getAmount());
    } catch (NumberFormatException e) {
      return false;
    }
  }
}
