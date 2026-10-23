package com.marketplace.dto.request;

import com.marketplace.enums.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Body của {@code POST /orders}: chỉ mua các dòng giỏ được chọn.
 *
 * @param cartItemIds id các dòng trong giỏ của chính customer; không rỗng, không trùng (service kiểm)
 * @param shippingAddress địa chỉ giao hàng, được chụp lại vào order
 * @param paymentMethod COD (thu tiền khi giao) hoặc VNPAY (thanh toán trước, response có link thanh toán)
 */
public record CheckoutRequest(
    @NotEmpty List<@NotNull UUID> cartItemIds,
    @NotBlank @Size(max = 1000) String shippingAddress,
    @NotNull PaymentMethod paymentMethod) {}
