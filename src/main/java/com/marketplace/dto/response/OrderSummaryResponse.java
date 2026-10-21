package com.marketplace.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.marketplace.enums.OrderStatus;
import com.marketplace.enums.PaymentMethod;
import com.marketplace.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Một dòng trong danh sách order của customer (không kèm các dòng hàng). */
public record OrderSummaryResponse(
    UUID id,
    OrderStatus status,
    PaymentStatus paymentStatus,
    PaymentMethod paymentMethod,
    Instant createdAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalAmount) {}
