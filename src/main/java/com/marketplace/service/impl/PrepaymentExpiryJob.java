package com.marketplace.service.impl;

import com.marketplace.common.util.TimeProvider;
import com.marketplace.repository.OrderRepository;
import com.marketplace.service.PaymentService;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job chạy nền mỗi 5 phút: huỷ order VNPAY quá 24 giờ chưa thanh toán và trả tồn kho (FR-ORD-10).
 *
 * <p>Mỗi order được huỷ trong transaction riêng ({@link PaymentService#cancelExpiredOrder}), nên một order
 * lỗi không làm hỏng các order khác. Render free ngủ khi không có request; lúc thức dậy, lần chạy đầu
 * tiên sẽ huỷ bù mọi order đã quá hạn trong lúc ngủ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PrepaymentExpiryJob {

  private final OrderRepository orderRepository;
  private final PaymentService paymentService;
  private final TimeProvider timeProvider;

  // fixedDelay: lần chạy sau bắt đầu 5 phút SAU KHI lần trước xong, nên hai lần chạy không bao giờ chồng nhau.
  @Scheduled(initialDelay = 1, fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
  public void cancelExpiredPrepaymentOrders() {
    Instant now = timeProvider.now();
    for (UUID orderId :
        orderRepository.findExpiredPrepaymentOrderIds(now.minus(PaymentService.PREPAYMENT_DEADLINE))) {
      try {
        if (paymentService.cancelExpiredOrder(orderId, now)) {
          log.info("Cancelled order {}: prepayment deadline expired", orderId);
        }
      } catch (RuntimeException e) {
        log.error("Could not cancel expired order {}", orderId, e);
      }
    }
  }
}
