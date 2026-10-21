package com.marketplace.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Cả giỏ hàng.
 *
 * @param items các dòng, mới thêm nhất trước
 * @param subtotal tổng thành tiền của các dòng MUA ĐƯỢC; tổng các dòng khách chọn do client tự tính
 */
public record CartResponse(
    List<CartItemResponse> items, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal subtotal) {

  public static CartResponse of(List<CartItemResponse> items) {
    BigDecimal subtotal =
        items.stream()
            .filter(CartItemResponse::purchasable)
            .map(CartItemResponse::lineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP); // giỏ rỗng vẫn ra "0.00"
    return new CartResponse(items, subtotal);
  }
}
