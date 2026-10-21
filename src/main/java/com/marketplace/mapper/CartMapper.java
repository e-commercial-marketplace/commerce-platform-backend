package com.marketplace.mapper;

import com.marketplace.dto.response.CartItemResponse;
import com.marketplace.entity.CartItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Đổi một dòng giỏ sang response, lấy giá và trạng thái từ product HIỆN TẠI. Tổng {@code subtotal}
 * là nghiệp vụ (chỉ cộng dòng mua được) nên tính ở {@code CartResponse.of}, không nằm trong mapper.
 */
@Mapper(componentModel = "spring")
public interface CartMapper {

  // id, quantity, createdAt cùng tên với CartItem nên tự map; các trường còn lại đọc từ product.
  @Mapping(target = "productId", source = "product.id")
  @Mapping(target = "name", source = "product.name")
  @Mapping(target = "imageUrl", source = "product.imageUrl")
  @Mapping(target = "unitPrice", source = "product.price")
  @Mapping(target = "availableStock", source = "product.stock")
  @Mapping(target = "active", source = "product.active")
  @Mapping(target = "sellerId", source = "product.seller.id") // proxy LAZY: đọc id không cần query thêm
  // Thành tiền = giá hiện tại x số lượng (giá trị tính ra, không có trường nguồn tương ứng).
  @Mapping(
      target = "lineTotal",
      expression =
          "java(item.getProduct().getPrice().multiply(java.math.BigDecimal.valueOf(item.getQuantity())))")
  CartItemResponse toItem(CartItem item);
}
