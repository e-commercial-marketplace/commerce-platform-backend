package com.marketplace.service.impl;

import com.marketplace.common.exception.BusinessException;
import com.marketplace.common.util.TimeProvider;
import com.marketplace.dto.request.CartItemAddRequest;
import com.marketplace.dto.request.CartItemUpdateRequest;
import com.marketplace.dto.response.CartResponse;
import com.marketplace.entity.CartItem;
import com.marketplace.entity.Product;
import com.marketplace.entity.User;
import com.marketplace.mapper.CartMapper;
import com.marketplace.repository.CartItemRepository;
import com.marketplace.repository.ProductRepository;
import com.marketplace.repository.UserRepository;
import com.marketplace.service.CartService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Giỏ hàng theo features/cart/API_CONTRACT.md. Giỏ không lưu giá: mỗi lần trả giỏ, giá đọc từ product
 * hiện tại. Thêm hay sửa đều kiểm tra product còn bán và đủ tồn kho; dòng có sẵn trong giỏ mà product
 * sau đó bị ẩn hoặc hết hàng vẫn được giữ, client dựa vào {@code active} và {@code availableStock}.
 */
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

  private final CartItemRepository cartItemRepository;
  private final ProductRepository productRepository;
  private final UserRepository userRepository;
  private final TimeProvider timeProvider;
  private final CartMapper cartMapper;

  @Override
  @Transactional(readOnly = true)
  public CartResponse getCart(UUID customerId) {
    return buildCart(customerId);
  }

  @Override
  @Transactional
  public CartResponse addItem(UUID customerId, CartItemAddRequest request) {
    // Bước 1: khoá dòng user để các thao tác sửa giỏ của cùng customer chạy lần lượt (xem UserRepository.lockRequired).
    User customer = userRepository.lockRequired(customerId);
    // Bước 2: product phải tồn tại (404) và còn bán (400 PRODUCT_INACTIVE).
    Product product =
        productRepository
            .findById(request.productId())
            .orElseThrow(() -> BusinessException.notFound("Product", request.productId()));
    product.ensureActive();
    // Bước 3: đã có dòng thì cộng dồn, chưa có thì tạo mới. Kiểm tồn kho trên TỔNG số lượng.
    CartItem existing =
        cartItemRepository.findByUserIdAndProductId(customerId, product.getId()).orElse(null);
    long current = existing == null ? 0 : existing.getQuantity();
    // Cộng bằng long để số lượng rất lớn không bị tràn int thành số âm rồi lọt qua kiểm tra tồn kho.
    long total = current + request.quantity();
    requireStock(product, total);
    if (existing == null) {
      cartItemRepository.save(CartItem.add(customer, product, (int) total, timeProvider.now()));
    } else {
      existing.setQuantity((int) total); // giữ nguyên createdAt nên vị trí trong giỏ không đổi
    }
    // Bước 4: trả cả giỏ sau khi thay đổi.
    return buildCart(customerId);
  }

  @Override
  @Transactional
  public CartResponse updateItem(UUID customerId, UUID cartItemId, CartItemUpdateRequest request) {
    userRepository.lockRequired(customerId);
    CartItem item = findOwnedItem(customerId, cartItemId);
    // Đặt lại số lượng (không cộng dồn); product vẫn phải còn bán và đủ hàng.
    item.getProduct().ensureActive();
    requireStock(item.getProduct(), request.quantity());
    item.setQuantity(request.quantity());
    return buildCart(customerId);
  }

  @Override
  @Transactional
  public CartResponse removeItem(UUID customerId, UUID cartItemId) {
    userRepository.lockRequired(customerId);
    // Xoá được cả dòng có product đã ẩn hay hết hàng: đó chính là cách khách dọn giỏ.
    cartItemRepository.delete(findOwnedItem(customerId, cartItemId));
    cartItemRepository.flush(); // xoá thật trước khi đọc lại giỏ
    return buildCart(customerId);
  }

  /** Đọc cả giỏ (mới nhất trước) và tính tiền theo giá hiện tại. */
  private CartResponse buildCart(UUID customerId) {
    return CartResponse.of(
        cartItemRepository.findByUserIdOrderByCreatedAtDescIdAsc(customerId).stream()
            .map(cartMapper::toItem)
            .toList());
  }


  /**
   * Theo contract: không có dòng với id đó thì 404 CART_ITEM_NOT_FOUND; dòng có thật nhưng thuộc
   * customer khác thì 403 FORBIDDEN.
   */
  private CartItem findOwnedItem(UUID customerId, UUID cartItemId) {
    CartItem item =
        cartItemRepository
            .findById(cartItemId)
            .orElseThrow(() -> BusinessException.cartItemNotFound("Cart item not found"));
    if (!item.getUser().getId().equals(customerId)) {
      throw BusinessException.forbidden("Cart item belongs to another customer");
    }
    return item;
  }

  private static void requireStock(Product product, long quantity) {
    if (quantity > product.getStock()) {
      throw BusinessException.insufficientStock(
          "Only " + product.getStock() + " item(s) left in stock");
    }
  }
}
