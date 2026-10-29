package com.marketplace.service;

import com.marketplace.dto.response.VnpayIpnResponse;
import com.marketplace.dto.response.VnpayPaymentResponse;
import com.marketplace.entity.Order;
import com.marketplace.enums.PaymentStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Thanh toán VNPAY theo features/payment/API_CONTRACT.md. */
public interface PaymentService {

  /** Order trả trước phải thanh toán trong 24 giờ kể từ lúc đặt, quá hạn thì bị huỷ (FR-ORD-10). */
  Duration PREPAYMENT_DEADLINE = Duration.ofHours(24);

  /**
   * Tạo lần thử VNPAY mới cho order (đã được khoá ở transaction của người gọi) và trả link thanh toán.
   * Checkout gọi hàm này cho lần đầu; retry cũng đi qua đây.
   */
  String createVnpayPayment(Order order, String clientIp);

  /** Customer thanh toán lại order VNPAY của mình: huỷ lần thử còn chờ, tạo lần thử và link mới. */
  VnpayPaymentResponse retryVnpayPayment(UUID customerId, UUID orderId, String clientIp);

  /** Xử lý kết quả VNPAY gửi về; IPN và Return URL dùng chung hàm này, gọi lặp không đổi gì thêm. */
  VnpayResult handleVnpayCallback(Map<String, String> params);

  /** Tạo payment COD chờ thu tiền khi giao, số tiền bằng tổng hiện tại của order. */
  void createCodPayment(Order order);

  /** Order trả trước qua VNPAY (có hạn 24 giờ); order COD thì thu tiền khi giao, không có hạn. */
  boolean isPaidByVnpay(Order order);

  /** Order đã có một lần thanh toán COMPLETED. */
  boolean hasCompletedPayment(Order order);

  /** Đã quá hạn thanh toán trước 24 giờ tính từ lúc đặt. */
  boolean isPaymentDeadlinePassed(Order order, Instant now);

  /**
   * Gọi sau khi tổng order đổi vì huỷ dòng. Payment đang chờ: COD nhận tổng mới (thu lúc giao); VNPAY
   * thành FAILED vì link đã gửi mang số tiền cũ. Order đã CANCELLED thì mọi payment đang chờ đều FAILED.
   */
  void updatePendingPaymentsAfterCancel(Order order);

  /** Order đã giao đủ: payment COD đang chờ được ghi nhận đã thu. VNPAY đã COMPLETED từ lúc trả tiền. */
  void markCodCollected(Order order, Instant now);

  /**
   * Kết quả xử lý một lần VNPAY gửi về.
   *
   * @param response câu trả lời cho VNPAY (RspCode)
   * @param orderId order của lần thử; {@code null} khi không xác định được (sai chữ ký, mã lạ)
   * @param paymentStatus trạng thái lần thử sau khi xử lý; {@code null} khi kết quả không hợp lệ
   */
  record VnpayResult(VnpayIpnResponse response, UUID orderId, PaymentStatus paymentStatus) {}
}
