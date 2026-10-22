package com.marketplace.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.marketplace.enums.OrderItemStatus;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Một dòng hàng của order. {@code productName} và {@code unitPrice} là bản chụp lúc checkout.
 *
 * @param sellerId seller chịu trách nhiệm dòng này (một order có thể có nhiều seller)
 */
public record OrderItemResponse(
    UUID id,
    UUID productId,
    String productName,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal unitPrice,
    int quantity,
    OrderItemStatus status,
    UUID sellerId) {}
