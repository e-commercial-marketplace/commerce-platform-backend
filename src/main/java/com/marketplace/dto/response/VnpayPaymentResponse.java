package com.marketplace.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.marketplace.enums.PaymentStatus;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Lần thử VNPAY vừa tạo. Client mở nguyên {@code paymentUrl}, không tự dựng hay ký lại.
 *
 * @param amount số tiền của lần thử, bằng tổng order hiện tại (chuỗi thập phân VND)
 */
public record VnpayPaymentResponse(
    UUID paymentId,
    UUID orderId,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
    PaymentStatus status,
    String paymentUrl) {}
