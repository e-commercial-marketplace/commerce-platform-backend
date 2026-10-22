package com.marketplace.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.marketplace.enums.OrderStatus;
import com.marketplace.enums.PaymentMethod;
import com.marketplace.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Chi tiết order (sau checkout và {@code GET /orders/{id}}). Tiền là chuỗi thập phân VND.
 *
 * @param paymentStatus trạng thái lần thanh toán mới nhất của order
 * @param voucherCode luôn {@code null} cho tới khi voucher được làm ở Phase 8
 */
public record OrderResponse(
    UUID id,
    OrderStatus status,
    PaymentStatus paymentStatus,
    PaymentMethod paymentMethod,
    Instant createdAt,
    String shippingAddress,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal shippingFee,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal discountAmount,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalAmount,
    String voucherCode,
    List<OrderItemResponse> items) {}
