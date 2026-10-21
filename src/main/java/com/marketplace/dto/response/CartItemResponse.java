package com.marketplace.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.marketplace.entity.CartItem;
import com.marketplace.entity.Product;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Một dòng trong giỏ, tính theo giá HIỆN TẠI của product. Tiền là chuỗi thập phân theo contract.
 *
 * @param availableStock tồn kho hiện tại; dòng có {@code quantity > availableStock} là không mua được
 * @param active product còn bán không; {@code false} thì client hiện "không có sẵn" và không cho chọn
 */
public record CartItemResponse(
    UUID id,
    UUID productId,
    String name,
    String imageUrl,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal unitPrice,
    int quantity,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal lineTotal,
    int availableStock,
    boolean active,
    UUID sellerId,
    Instant createdAt) {

  public static CartItemResponse from(CartItem item) {
    Product product = item.getProduct();
    return new CartItemResponse(
        item.getId(),
        product.getId(),
        product.getName(),
        product.getImageUrl(),
        product.getPrice(),
        item.getQuantity(),
        product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())), // thành tiền = giá x số lượng
        product.getStock(),
        product.isActive(),
        product.getSeller().getId(), // proxy LAZY: đọc id không cần query thêm
        item.getCreatedAt());
  }

  /** Mua được khi product còn bán và tồn kho đủ cho số lượng trong giỏ. */
  public boolean purchasable() {
    return active && quantity <= availableStock;
  }
}
