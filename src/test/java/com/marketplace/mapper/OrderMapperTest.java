package com.marketplace.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.entity.Order;
import com.marketplace.enums.PaymentMethod;
import java.math.BigDecimal;
import com.marketplace.enums.OrderStatus;
import com.marketplace.entity.Payment;
import com.marketplace.enums.PaymentStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Lần thanh toán hiện tại không phụ thuộc thứ tự danh sách (DB không đảm bảo thứ tự đọc lên). */
class OrderMapperTest {

  private final OrderMapper mapper = new OrderMapperImpl();

  @Test
  void aCompletedAttemptWinsEvenWhenItIsNotLastInTheList() {
    Order order = Order.builder().customer(null).shippingAddress("A").createdAt(Instant.now()).status(OrderStatus.PENDING).totalAmount(new BigDecimal("0.00")).build();
    Payment completed = Payment.builder().order(order).method(PaymentMethod.VNPAY).status(PaymentStatus.PENDING).amount(order.getTotalAmount()).transactionId("completed").build();
    completed.setStatus(PaymentStatus.COMPLETED);
    Payment failed = Payment.builder().order(order).method(PaymentMethod.VNPAY).status(PaymentStatus.PENDING).amount(order.getTotalAmount()).transactionId("failed").build();
    failed.setStatus(PaymentStatus.FAILED);
    order.getPayments().add(completed);
    order.getPayments().add(failed); // lần đã trả nằm trước, như khi DB trả hàng không theo thứ tự thêm

    assertThat(mapper.currentPayment(order).getStatus()).isEqualTo(PaymentStatus.COMPLETED);
  }

  @Test
  void aPendingAttemptBeatsFailedOnesAndTheLastIsTheFallback() {
    Order order = Order.builder().customer(null).shippingAddress("A").createdAt(Instant.now()).status(OrderStatus.PENDING).totalAmount(new BigDecimal("0.00")).build();
    Payment pending = Payment.builder().order(order).method(PaymentMethod.VNPAY).status(PaymentStatus.PENDING).amount(order.getTotalAmount()).transactionId("pending").build();
    Payment failed = Payment.builder().order(order).method(PaymentMethod.VNPAY).status(PaymentStatus.PENDING).amount(order.getTotalAmount()).transactionId("failed").build();
    failed.setStatus(PaymentStatus.FAILED);
    order.getPayments().add(pending);
    order.getPayments().add(failed);
    assertThat(mapper.currentPayment(order).getStatus()).isEqualTo(PaymentStatus.PENDING);

    pending.setStatus(PaymentStatus.FAILED); // không còn lần nào đang chờ hay hoàn tất: lấy lần cuối
    assertThat(mapper.currentPayment(order)).isSameAs(failed);
  }
}
