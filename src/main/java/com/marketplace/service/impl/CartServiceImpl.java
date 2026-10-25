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
import com.marketplace.repository.CartCache;
import com.marketplace.repository.CartCache.CachedLine;
import com.marketplace.repository.CartItemRepository;
import com.marketplace.repository.ProductRepository;
import com.marketplace.repository.UserRepository;
import com.marketplace.service.CartService;
import com.marketplace.service.ProductService;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Giỏ hàng theo features/cart/API_CONTRACT.md. Giỏ không lưu giá: mỗi lần trả giỏ, giá đọc từ product
 * hiện tại. Thêm hay sửa đều kiểm tra product còn bán và đủ tồn kho; dòng có sẵn trong giỏ mà product
 * sau đó bị ẩn hoặc hết hàng vẫn được giữ, client dựa vào {@code active} và {@code availableStock}.
 *
 * <p>Đọc giỏ đi qua {@link CartCache} (Redis, tuỳ chọn): chỉ các dòng giỏ được cache, product luôn đọc
 * từ PostgreSQL. Mọi thao tác sửa giỏ ghi PostgreSQL rồi xoá cache sau khi commit.
 */
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

  /** Cùng thứ tự với query của repository: mới thêm nhất trước, id làm khoá phụ. */
  private static final Comparator<CachedLine> NEWEST_FIRST =
      Comparator.comparing(CachedLine::createdAt)
          .reversed()
          // So chuỗi UUID (chữ thường) cho cùng thứ tự với kiểu uuid của PostgreSQL; UUID.compareTo thì khác.
          .thenComparing(line -> line.id().toString());

  private final CartItemRepository cartItemRepository;
  private final ProductRepository productRepository;
  private final ProductService productService;
  private final UserRepository userRepository;
  private final TimeProvider timeProvider;
  private final CartMapper cartMapper;
  private final CartCache cartCache;

  @Override
  @Transactional(readOnly = true)
  public CartResponse getCart(UUID customerId) {
    // Bước 1: có bản sao trong Redis thì dùng các dòng đó, ghép với product hiện tại.
    // Bước 2: không có (hoặc bản sao trỏ tới product không còn) thì đọc PostgreSQL rồi cache lại.
    List<CartItem> items =
        cartCache
            .get(customerId)
            .flatMap(this::withCurrentProducts)
            .orElseGet(
                () -> {
                  List<CartItem> fromDb = cartItemRepository.findByUserIdOrderByCreatedAtDescIdAsc(customerId);
                  cartCache.put(customerId, fromDb);
                  return fromDb;
                });
    return toResponse(items);
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
    productService.ensureActive(product);
    // Bước 3: đã có dòng thì cộng dồn, chưa có thì tạo mới. Kiểm tồn kho trên TỔNG số lượng.
    CartItem existing =
        cartItemRepository.findByUserIdAndProductId(customerId, product.getId()).orElse(null);
    long current = existing == null ? 0 : existing.getQuantity();
    // Cộng bằng long để số lượng rất lớn không bị tràn int thành số âm rồi lọt qua kiểm tra tồn kho.
    long total = current + request.quantity();
    requireStock(product, total);
    if (existing == null) {
      cartItemRepository.save(
          CartItem.builder()
              .user(customer)
              .product(product)
              .quantity((int) total)
              .createdAt(timeProvider.now())
              .build());
    } else {
      existing.setQuantity((int) total); // giữ nguyên createdAt nên vị trí trong giỏ không đổi
    }
    cartCache.evictAfterCommit(customerId);
    // Bước 4: trả cả giỏ sau khi thay đổi.
    return buildCart(customerId);
  }

  @Override
  @Transactional
  public CartResponse updateItem(UUID customerId, UUID cartItemId, CartItemUpdateRequest request) {
    userRepository.lockRequired(customerId);
    CartItem item = findOwnedItem(customerId, cartItemId);
    // Đặt lại số lượng (không cộng dồn); product vẫn phải còn bán và đủ hàng.
    productService.ensureActive(item.getProduct());
    requireStock(item.getProduct(), request.quantity());
    item.setQuantity(request.quantity());
    cartCache.evictAfterCommit(customerId);
    return buildCart(customerId);
  }

  @Override
  @Transactional
  public CartResponse removeItem(UUID customerId, UUID cartItemId) {
    userRepository.lockRequired(customerId);
    // Xoá được cả dòng có product đã ẩn hay hết hàng: đó chính là cách khách dọn giỏ.
    cartItemRepository.delete(findOwnedItem(customerId, cartItemId));
    cartItemRepository.flush(); // xoá thật trước khi đọc lại giỏ
    cartCache.evictAfterCommit(customerId);
    return buildCart(customerId);
  }

  /**
   * Đọc cả giỏ từ PostgreSQL (mới nhất trước) và tính tiền theo giá hiện tại. Các thao tác sửa giỏ dùng
   * hàm này, không dùng cache, vì cache chỉ được xoá sau khi commit.
   */
  private CartResponse buildCart(UUID customerId) {
    return toResponse(cartItemRepository.findByUserIdOrderByCreatedAtDescIdAsc(customerId));
  }

  private CartResponse toResponse(List<CartItem> items) {
    return CartResponse.of(items.stream().map(cartMapper::toItem).toList());
  }

  /**
   * Ghép các dòng đã cache với product hiện tại (một query cho mọi product), thành các CartItem tạm (không
   * lưu) để dùng chung mapper. Thiếu product nào thì trả rỗng, để người gọi đọc PostgreSQL.
   */
  private Optional<List<CartItem>> withCurrentProducts(List<CachedLine> lines) {
    Set<UUID> productIds = lines.stream().map(CachedLine::productId).collect(Collectors.toSet());
    Map<UUID, Product> products =
        productRepository.findAllById(productIds).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));
    if (products.size() != productIds.size()) {
      return Optional.empty();
    }
    return Optional.of(
        lines.stream()
            .sorted(NEWEST_FIRST)
            .map(
                line ->
                    CartItem.builder()
                        .id(line.id())
                        .product(products.get(line.productId()))
                        .quantity(line.quantity())
                        .createdAt(line.createdAt())
                        .build())
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
