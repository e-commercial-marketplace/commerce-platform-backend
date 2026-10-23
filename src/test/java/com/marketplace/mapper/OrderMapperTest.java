package com.marketplace.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.entity.Order;
import com.marketplace.entity.Payment;
import com.marketplace.enums.PaymentStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Lần thanh toán hiện tại không phụ thuộc thứ tự danh sách (DB không đảm bảo thứ tự đọc lên). */
class OrderMapperTest {

  private final OrderMapper mapper = new OrderMapperImpl();

  @Test
  void aCompletedAttemptWinsEvenWhenItIsNotLastInTheList() {
    Order order = Order.place(null, "A", Instant.now());
    Payment completed = Payment.pendingVnpay(order, "completed");
    completed.complete(Instant.now());
    Payment failed = Payment.pendingVnpay(order, "failed");
    failed.markFailed();
    order.getPayments().add(completed);
    order.getPayments().add(failed); // lần đã trả nằm trước, như khi DB trả hàng không theo thứ tự thêm

    assertThat(mapper.currentPayment(order).getStatus()).isEqualTo(PaymentStatus.COMPLETED);
  }

  @Test
  void aPendingAttemptBeatsFailedOnesAndTheLastIsTheFallback() {
    Order order = Order.place(null, "A", Instant.now());
    Payment pending = Payment.pendingVnpay(order, "pending");
    Payment failed = Payment.pendingVnpay(order, "failed");
    failed.markFailed();
    order.getPayments().add(pending);
    order.getPayments().add(failed);
    assertThat(mapper.currentPayment(order).getStatus()).isEqualTo(PaymentStatus.PENDING);

    pending.markFailed(); // không còn lần nào đang chờ hay hoàn tất: lấy lần cuối
    assertThat(mapper.currentPayment(order)).isSameAs(failed);
  }
}
