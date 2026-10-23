package com.marketplace.service.impl;

import com.marketplace.common.util.TimeProvider;
import com.marketplace.repository.OrderRepository;
import com.marketplace.service.OrderService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job chạy nền mỗi giờ: order DELIVERED mà khách không xác nhận đã nhận hàng trong 3 ngày kể từ lần giao
 * cuối thì tự chuyển COMPLETED (FR-ORD-13). Hạn tính theo ngày nên chạy mỗi giờ là đủ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCompletionJob {

  private final OrderRepository orderRepository;
  private final TimeProvider timeProvider;

  @Scheduled(initialDelay = 2, fixedDelay = 60, timeUnit = TimeUnit.MINUTES)
  public void completeUnconfirmedOrders() {
    int completed =
        orderRepository.completeDeliveredBefore(
            timeProvider.now().minus(OrderService.AUTO_COMPLETE_AFTER));
    if (completed > 0) {
      log.info("Auto-completed {} delivered order(s) without customer confirmation", completed);
    }
  }
}
