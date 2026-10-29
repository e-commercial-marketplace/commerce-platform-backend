package com.marketplace.service.impl;

import com.marketplace.common.exception.BusinessException;
import com.marketplace.common.util.TimeProvider;
import com.marketplace.dto.response.VnpayIpnResponse;
import com.marketplace.dto.response.VnpayPaymentResponse;
import com.marketplace.entity.Order;
import com.marketplace.entity.Payment;
import com.marketplace.enums.OrderStatus;
import com.marketplace.enums.PaymentMethod;
import com.marketplace.enums.PaymentStatus;
import com.marketplace.repository.OrderRepository;
import com.marketplace.repository.PaymentRepository;
import com.marketplace.service.PaymentService;
import com.marketplace.vnpay.VnpayGateway;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thanh toán COD và VNPAY. Mọi thao tác đổi trạng thái đều KHOÁ order trước (cùng khoá với seller huỷ
 * dòng), rồi mới đọc payment, nên IPN, Return URL, retry, seller huỷ và job 24 giờ chạy lần lượt trên cùng
 * một order và luôn thấy dữ liệu mới nhất.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

  // VNPAY báo thành công khi cả mã phản hồi và trạng thái giao dịch đều là "00".
  private static final String VNPAY_SUCCESS = "00";

  private final OrderRepository orderRepository;
  private final PaymentRepository paymentRepository;
  private final VnpayGateway vnpayGateway;
  private final TimeProvider timeProvider;

  @Override
  public void createCodPayment(Order order) {
    // COD chờ thu tiền khi giao, số tiền bằng tổng của order lúc này.
    Payment payment =
        Payment.builder()
            .order(order)
            .method(PaymentMethod.COD)
            .status(PaymentStatus.PENDING)
            .amount(order.getTotalAmount())
            .build();
    order.getPayments().add(paymentRepository.save(payment));
  }

  @Override
  public String createVnpayPayment(Order order, String clientIp) {
    // Mã tham chiếu gửi VNPAY (vnp_TxnRef): UUID bỏ dấu gạch (32 ký tự chữ và số), duy nhất nhờ unique
    // index trên transaction_id. Số tiền bằng tổng hiện tại của order.
    String txnRef = UUID.randomUUID().toString().replace("-", "");
    Payment payment =
        paymentRepository.save(
            Payment.builder()
                .order(order)
                .method(PaymentMethod.VNPAY)
                .status(PaymentStatus.PENDING)
                .amount(order.getTotalAmount())
                .transactionId(txnRef)
                .build());
    order.getPayments().add(payment);
    Instant deadline = order.getCreatedAt().plus(PREPAYMENT_DEADLINE);
    return vnpayGateway.buildPaymentUrl(payment, clientIp, timeProvider.now(), deadline);
  }

  @Override
  @Transactional
  public VnpayPaymentResponse retryVnpayPayment(UUID customerId, UUID orderId, String clientIp) {
    // Bước 1: khoá order; không có hoặc của customer khác thì 404 (không lộ order người khác có tồn tại).
    Order order =
        orderRepository
            .findByIdForUpdate(orderId)
            .filter(o -> o.getCustomer().getId().equals(customerId))
            .orElseThrow(() -> BusinessException.notFound("Order", orderId));
    // Bước 2: chỉ order VNPAY còn chờ, chưa trả và còn trong hạn 24 giờ mới thanh toán được.
    Instant now = timeProvider.now();
    if (order.getStatus() != OrderStatus.PENDING || !isPaidByVnpay(order) || hasCompletedPayment(order)) {
      throw BusinessException.orderNotPayable("The order cannot be paid with VNPAY");
    }
    if (isPaymentDeadlinePassed(order, now)) {
      throw BusinessException.orderNotPayable("The payment deadline has passed");
    }
    // Bước 3: link cũ (nếu còn) hết dùng được, để mỗi lúc chỉ có một link thanh toán được cho order.
    order.getPayments().stream().filter(PaymentServiceImpl::isVnpay).forEach(PaymentServiceImpl::markFailed);
    // Bước 4: lần thử mới với tổng hiện tại và link mới.
    String paymentUrl = createVnpayPayment(order, clientIp);
    Payment attempt = order.getPayments().get(order.getPayments().size() - 1);
    return new VnpayPaymentResponse(
        attempt.getId(), order.getId(), attempt.getAmount(), attempt.getStatus(), paymentUrl);
  }

  @Override
  @Transactional
  public VnpayResult handleVnpayCallback(Map<String, String> params) {
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
    if (payment.getStatus() != PaymentStatus.PENDING) {
      if (success && payment.getStatus() != PaymentStatus.COMPLETED) {
        // Khách đã trả tiền cho một lần thử đã bị huỷ (có retry, tổng đổi, hoặc order hết hạn): cần hoàn tay.
        log.warn(
            "VNPAY success for non-payable attempt txnRef={} order={}: refund manually",
            txnRef,
            orderId);
      }
      return new VnpayResult(VnpayIpnResponse.ALREADY_CONFIRMED, orderId, payment.getStatus());
    }
    // Bước 5: ghi nhận kết quả. Thành công: payment COMPLETED và order PENDING -> PAID; thất bại: chỉ
    // payment FAILED, order vẫn chờ để khách thanh toán lại.
    if (success) {
      complete(payment, timeProvider.now());
      if (order.getStatus() == OrderStatus.PENDING) {
        order.setStatus(OrderStatus.PAID);
      }
    } else {
      markFailed(payment);
    }
    log.info("VNPAY result txnRef={} order={} -> {}", txnRef, orderId, payment.getStatus());
    return new VnpayResult(VnpayIpnResponse.CONFIRMED, orderId, payment.getStatus());
  }

  @Override
  public boolean isPaidByVnpay(Order order) {
    return order.getPayments().stream().anyMatch(PaymentServiceImpl::isVnpay);
  }

  @Override
  public boolean hasCompletedPayment(Order order) {
    return order.getPayments().stream().anyMatch(p -> p.getStatus() == PaymentStatus.COMPLETED);
  }

  @Override
  public boolean isPaymentDeadlinePassed(Order order, Instant now) {
    return !now.isBefore(order.getCreatedAt().plus(PREPAYMENT_DEADLINE));
  }

  @Override
  public void updatePendingPaymentsAfterCancel(Order order) {
    boolean orderCancelled = order.getStatus() == OrderStatus.CANCELLED;
    for (Payment payment : order.getPayments()) {
      if (payment.getStatus() != PaymentStatus.PENDING) {
        continue;
      }
      if (isVnpay(payment)) {
        payment.setStatus(PaymentStatus.FAILED); // link đã gửi mang số tiền cũ, khách thanh toán lại
      } else {
        payment.setAmount(order.getTotalAmount()); // COD thu lúc giao nên nhận tổng mới
      }
      if (orderCancelled) {
        markFailed(payment); // không còn gì để thu
      }
    }
  }

  @Override
  public void markCodCollected(Order order, Instant now) {
    order.getPayments().stream()
        .filter(payment -> !isVnpay(payment))
        .forEach(payment -> complete(payment, now));
  }

  private static boolean isVnpay(Payment payment) {
    return payment.getMethod() == PaymentMethod.VNPAY;
  }

  /** Đã thu được tiền. Chỉ đi được từ PENDING; trạng thái khác giữ nguyên. */
  private static void complete(Payment payment, Instant now) {
    if (payment.getStatus() == PaymentStatus.PENDING) {
      payment.setStatus(PaymentStatus.COMPLETED);
      payment.setPaidAt(now);
    }
  }

  /** Không còn thu được: payment đang chờ kết thúc ở FAILED. */
  private static void markFailed(Payment payment) {
    if (payment.getStatus() == PaymentStatus.PENDING) {
      payment.setStatus(PaymentStatus.FAILED);
    }
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
