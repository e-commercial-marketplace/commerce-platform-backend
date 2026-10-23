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
  String openVnpayAttempt(Order order, String clientIp);

  /** Customer thanh toán lại order VNPAY của mình: huỷ lần thử còn chờ, tạo lần thử và link mới. */
  VnpayPaymentResponse retryVnpay(UUID customerId, UUID orderId, String clientIp);

  /** Xử lý kết quả VNPAY gửi về; IPN và Return URL dùng chung hàm này, gọi lặp không đổi gì thêm. */
  VnpayResult handleVnpayResult(Map<String, String> params);

  /**
   * Huỷ order trả trước đã quá hạn 24 giờ: huỷ các dòng còn lại, trả kho đúng một lần, payment đang chờ
   * thành FAILED. Kiểm lại điều kiện sau khi khoá order; không còn quá hạn thì không làm gì.
   *
   * @return {@code true} nếu order vừa bị huỷ
   */
  boolean cancelExpiredOrder(UUID orderId, Instant now);

  /**
   * Kết quả xử lý một lần VNPAY gửi về.
   *
   * @param response câu trả lời cho VNPAY (RspCode)
   * @param orderId order của lần thử; {@code null} khi không xác định được (sai chữ ký, mã lạ)
   * @param paymentStatus trạng thái lần thử sau khi xử lý; {@code null} khi kết quả không hợp lệ
   */
  record VnpayResult(VnpayIpnResponse response, UUID orderId, PaymentStatus paymentStatus) {}
}
