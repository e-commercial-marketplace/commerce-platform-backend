package com.marketplace.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Body của {@code POST /payments/vnpay/create}: thanh toán lại một order VNPAY. Không nhận URL quay về
 * từ client (URL đó do server cấu hình), để endpoint không bị dùng làm open redirect.
 *
 * @param orderId order của chính customer đang đăng nhập
 */
public record VnpayCreateRequest(@NotNull UUID orderId) {}
