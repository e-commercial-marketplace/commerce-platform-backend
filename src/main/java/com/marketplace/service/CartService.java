package com.marketplace.service;

import com.marketplace.dto.request.CartItemAddRequest;
import com.marketplace.dto.request.CartItemUpdateRequest;
import com.marketplace.dto.response.CartResponse;
import java.util.UUID;

/** Giỏ hàng của customer đang đăng nhập; mọi hàm trả về cả giỏ sau khi thay đổi. */
public interface CartService {

  CartResponse getCart(UUID customerId);

  /** Thêm product; đã có trong giỏ thì cộng dồn số lượng. */
  CartResponse addItem(UUID customerId, CartItemAddRequest request);

  /** Đặt lại số lượng của một dòng thuộc giỏ của customer. */
  CartResponse updateItem(UUID customerId, UUID cartItemId, CartItemUpdateRequest request);

  CartResponse removeItem(UUID customerId, UUID cartItemId);
}
